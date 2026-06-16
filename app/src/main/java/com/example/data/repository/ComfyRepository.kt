package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.database.*
import com.example.data.network.ComfyClient
import com.example.data.network.GenerationStatus
import com.example.data.settings.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ComfyRepository(
    private val context: Context,
    val comfyClient: ComfyClient,
    private val database: AppDatabase,
    private val settingsManager: SettingsManager,
    private val externalScope: CoroutineScope
) {
    private val TAG = "ComfyRepository"
    private val imageDao = database.imageDao()
    private val workflowDao = database.workflowDao()
    private val promptDao = database.promptDao()

    // Database flow exposures
    val allImages: Flow<List<GeneratedImage>> = imageDao.getAllImages()
    val allPresets: Flow<List<WorkflowPreset>> = workflowDao.getAllPresets()
    val promptHistory: Flow<List<PromptHistory>> = promptDao.getRecentHistory()
    val favoritePrompts: Flow<List<FavoritePrompt>> = promptDao.getFavoritePrompts()

    // Active state for current generation image results
    private val _lastGeneratedImage = MutableStateFlow<GeneratedImage?>(null)
    val lastGeneratedImage = _lastGeneratedImage.asStateFlow()

    private val _generationError = MutableStateFlow<String?>(null)
    val generationError = _generationError.asStateFlow()

    init {
        // Observe comfyClient's generation state to automatically download the result image when successful
        comfyClient.generationStatus
            .onEach { status ->
                if (status == GenerationStatus.SUCCESS) {
                    val promptId = comfyClient.promptId.value
                    if (promptId != null) {
                        fetchAndSaveResult(promptId)
                    }
                }
            }
            .launchIn(externalScope)
    }

    private fun fetchAndSaveResult(promptId: String) {
        externalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching history for prompt ID: $promptId")
                delay(1000) // Small delay to guarantee history is written
                var historyObj: JSONObject? = null
                for (attempt in 1..5) {
                    historyObj = comfyClient.getPromptHistory(promptId)
                    if (historyObj != null && historyObj.has(promptId)) {
                        break
                    }
                    delay(1000)
                }

                if (historyObj == null || !historyObj.has(promptId)) {
                    _generationError.value = "Failed to fetch generation history from ComfyUI."
                    return@launch
                }

                val jobDetail = historyObj.getJSONObject(promptId)
                val outputs = jobDetail.optJSONObject("outputs") ?: return@launch
                val promptDetails = jobDetail.optJSONObject("prompt")

                // Find positive/negative prompts from the history job info
                val (positive, negative, steps, cfg, width, height, seed, sampler) = extractGenerationMetadata(promptDetails)

                // Retrieve output images
                val keys = outputs.keys()
                while (keys.hasNext()) {
                    val nodeKey = keys.next()
                    val nodeOutput = outputs.getJSONObject(nodeKey)
                    if (nodeOutput.has("images")) {
                        val imagesArray = nodeOutput.getJSONArray("images")
                        for (i in 0 until imagesArray.length()) {
                            val imgObj = imagesArray.getJSONObject(i)
                            val filename = imgObj.getString("filename")
                            val subfolder = imgObj.optString("subfolder", "")
                            val type = imgObj.optString("type", "output")

                            Log.d(TAG, "Downloading image: $filename")
                            downloadAndStoreImage(filename, subfolder, type, positive, negative, steps, cfg, width, height, seed, sampler)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving generation results", e)
                _generationError.value = e.message
            }
        }
    }

    private fun extractGenerationMetadata(promptDetails: JSONObject?): MetadataPack {
        if (promptDetails == null) return MetadataPack()

        var positive = ""
        var negative = ""
        var steps = 20
        var cfg = 7.0f
        var width = 512
        var height = 512
        var seed = 0L
        var sampler = "euler"

        try {
            val keys = promptDetails.keys()
            while (keys.hasNext()) {
                val nodeKey = keys.next()
                val node = promptDetails.getJSONObject(nodeKey)
                val classType = node.optString("class_type")
                val inputs = node.optJSONObject("inputs") ?: continue

                when (classType) {
                    "KSampler" -> {
                        steps = inputs.optInt("steps", steps)
                        cfg = inputs.optDouble("cfg", cfg.toDouble()).toFloat()
                        seed = inputs.optLong("seed", seed)
                        sampler = inputs.optString("sampler_name", sampler)
                    }
                    "EmptyLatentImage" -> {
                        width = inputs.optInt("width", width)
                        height = inputs.optInt("height", height)
                    }
                    "CLIPTextEncode" -> {
                        val text = inputs.optString("text")
                        // Heuristically separate positive vs negative by finding mentions or matching KSampler connectors if we could.
                        // For metadata saving, if positive is empty, fill it. Else fill negative.
                        if (positive.isEmpty()) {
                            positive = text
                        } else if (negative.isEmpty()) {
                            negative = text
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing metadata pack", e)
        }

        return MetadataPack(positive, negative, steps, cfg, width, height, seed, sampler)
    }

    private data class MetadataPack(
        val positive: String = "",
        val negative: String = "",
        val steps: Int = 20,
        val cfg: Float = 7.0f,
        val width: Int = 512,
        val height: Int = 512,
        val seed: Long = 0L,
        val sampler: String = "euler"
    )

    private suspend fun downloadAndStoreImage(
        filename: String,
        subfolder: String,
        type: String,
        positive: String,
        negative: String?,
        steps: Int,
        cfg: Float,
        width: Int,
        height: Int,
        seed: Long,
        sampler: String
    ) = withContext(Dispatchers.IO) {
        val stream = comfyClient.getImageViewStream(filename, subfolder, type)
        if (stream == null) {
            Log.e(TAG, "Failed to get image stream from ComfyUI.")
            return@withContext
        }

        val fileBytes = stream.readBytes()
        stream.close()

        // 1. Save to app-specific internal directory
        val appFolder = File(context.filesDir, "generated_images")
        if (!appFolder.exists()) {
            appFolder.mkdirs()
        }

        val uniqueFilename = "comfypad_${System.currentTimeMillis()}_$filename"
        val internalFile = File(appFolder, uniqueFilename)
        FileOutputStream(internalFile).use { fos ->
            fos.write(fileBytes)
        }

        var galleryUriStr: String? = null

        // 2. Save optionally to device gallery via MediaStore
        if (settingsManager.saveToGallery) {
            galleryUriStr = saveToGallery(fileBytes, uniqueFilename)
        }

        // 3. Put in local database
        val generatedImage = GeneratedImage(
            fileName = uniqueFilename,
            localPath = internalFile.absolutePath,
            prompt = positive.ifEmpty { "Generative Art" },
            negativePrompt = negative,
            steps = steps,
            cfg = cfg,
            width = width,
            height = height,
            seed = seed,
            sampler = sampler,
            timestamp = System.currentTimeMillis()
        )

        val id = imageDao.insertImage(generatedImage)
        val insertedImg = generatedImage.copy(id = id.toInt())
        _lastGeneratedImage.value = insertedImg

        // Record prompt history
        if (positive.trim().isNotEmpty()) {
            insertPromptHistory(positive.trim())
        }
    }

    private fun saveToGallery(fileBytes: ByteArray, filename: String): String? {
        val resolver = context.contentResolver
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ComfyPad")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(imageCollection, contentValues) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { os ->
                os.write(fileBytes)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            } else {
                MediaScannerConnection.scanFile(context, arrayOf(File(Environment.getExternalStorageDirectory(), "Pictures/ComfyPad/$filename").absolutePath), null, null)
            }
            return uri.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to MediaStore", e)
            resolver.delete(uri, null, null)
            return null
        }
    }

    // DB wrappers
    suspend fun insertWorkflow(preset: WorkflowPreset) = withContext(Dispatchers.IO) {
        workflowDao.insertPreset(preset)
    }

    suspend fun deleteWorkflowById(id: Int) = withContext(Dispatchers.IO) {
        workflowDao.deletePresetById(id)
    }

    suspend fun updateWorkflow(preset: WorkflowPreset) = withContext(Dispatchers.IO) {
        workflowDao.updatePreset(preset)
    }

    suspend fun deleteImage(image: GeneratedImage) = withContext(Dispatchers.IO) {
        // Delete internal file
        try {
            val file = File(image.localPath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: ${image.localPath}", e)
        }
        imageDao.deleteImageById(image.id)
    }

    suspend fun deleteBulkImages(images: List<GeneratedImage>) = withContext(Dispatchers.IO) {
        images.forEach { image ->
            try {
                val file = File(image.localPath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting file bulk", e)
            }
        }
        imageDao.deleteImagesByIds(images.map { it.id })
    }

    suspend fun updateImage(image: GeneratedImage) = withContext(Dispatchers.IO) {
        imageDao.updateImage(image)
    }

    suspend fun insertPromptHistory(prompt: String) = withContext(Dispatchers.IO) {
        promptDao.deleteHistoryItemByPrompt(prompt) // ensure uniqueness of most recent
        promptDao.insertHistoryItem(PromptHistory(prompt = prompt, timestamp = System.currentTimeMillis()))
    }

    suspend fun toggleFavoritePrompt(prompt: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        if (isFavorite) {
            promptDao.insertFavorite(FavoritePrompt(prompt = prompt, timestamp = System.currentTimeMillis()))
        } else {
            promptDao.deleteFavorite(prompt)
        }
    }

    fun clearCache() {
        val appFolder = File(context.filesDir, "generated_images")
        if (appFolder.exists()) {
            val files = appFolder.listFiles()
            files?.forEach { file ->
                file.delete()
            }
        }
    }

    // Tracing/rewriting custom flow graph helper!
    fun modifyWorkflowJson(
        templateJson: String,
        positivePrompt: String,
        negativePrompt: String,
        seed: Long,
        steps: Int,
        cfg: Float,
        sampler: String,
        width: Int,
        height: Int
    ): String {
        try {
            val root = JSONObject(templateJson)
            val keys = root.keys()

            // 1. Trace nodes by scanning class_types
            val ksamplers = mutableListOf<JSONObject>()
            val emptyLatents = mutableListOf<JSONObject>()
            val clipEncodes = mutableListOf<CLIPTrace>()

            // We scan the root nodes
            while (keys.hasNext()) {
                val nodeId = keys.next()
                val nodeObj = root.optJSONObject(nodeId) ?: continue
                val classType = nodeObj.optString("class_type")
                val inputs = nodeObj.optJSONObject("inputs") ?: continue

                when (classType) {
                    "KSampler" -> ksamplers.add(nodeObj)
                    "EmptyLatentImage" -> emptyLatents.add(nodeObj)
                    "CLIPTextEncode" -> {
                        clipEncodes.add(CLIPTrace(nodeId, nodeObj, inputs))
                    }
                }
            }

            // 2. Apply modifications to EmptyLatentImages
            emptyLatents.forEach { node ->
                val inputs = node.optJSONObject("inputs") ?: return@forEach
                inputs.put("width", width)
                inputs.put("height", height)
            }

            // 3. Apply modifications to KSamplers
            ksamplers.forEach { node ->
                val inputs = node.optJSONObject("inputs") ?: return@forEach
                inputs.put("seed", seed)
                inputs.put("steps", steps)
                inputs.put("cfg", cfg.toDouble())
                inputs.put("sampler_name", sampler)
            }

            // 4. Distinguish Positive and Negative CLIPTextEncodes and mutate them
            // In ComfyUI, KSampler positive input points to [CLIPTextEncodeId, Index]
            if (ksamplers.isNotEmpty()) {
                val primarySampler = ksamplers[0]
                val kickerInputs = primarySampler.optJSONObject("inputs")
                if (kickerInputs != null) {
                    val posArray = kickerInputs.optJSONArray("positive")
                    val negArray = kickerInputs.optJSONArray("negative")

                    val posId = posArray?.optString(0)
                    val negId = negArray?.optString(0)

                    clipEncodes.forEach { trace ->
                        if (trace.id == posId) {
                            trace.inputs.put("text", positivePrompt)
                        } else if (trace.id == negId) {
                            trace.inputs.put("text", negativePrompt)
                        }
                    }
                }
            } else {
                // Heuristic: if we couldn't match connections, and there are standard nodes:
                if (clipEncodes.size == 2) {
                    clipEncodes[0].inputs.put("text", positivePrompt)
                    clipEncodes[1].inputs.put("text", negativePrompt)
                } else if (clipEncodes.size == 1) {
                    clipEncodes[0].inputs.put("text", positivePrompt)
                }
            }

            return root.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error modifying workflow json", e)
            return templateJson
        }
    }

    private data class CLIPTrace(val id: String, val node: JSONObject, val inputs: JSONObject)
}
