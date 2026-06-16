package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.GeneratedImage
import com.example.data.database.WorkflowPreset
import com.example.data.network.ComfyClient
import com.example.data.network.ConnectionStatus
import com.example.data.network.GenerationStatus
import com.example.data.repository.ComfyRepository
import com.example.data.settings.SettingsManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    private val context = application.applicationContext

    // Dependencies
    val settingsManager = SettingsManager(context)
    val database = AppDatabase.getDatabase(context)
    val comfyClient = ComfyClient(settingsManager, viewModelScope)
    val repository = ComfyRepository(context, comfyClient, database, settingsManager, viewModelScope)

    // Current State Flow Inputs
    private val _positivePrompt = MutableStateFlow("")
    val positivePrompt = _positivePrompt.asStateFlow()

    private val _negativePrompt = MutableStateFlow("")
    val negativePrompt = _negativePrompt.asStateFlow()

    private val _steps = MutableStateFlow(20)
    val steps = _steps.asStateFlow()

    private val _cfg = MutableStateFlow(7.0f)
    val cfg = _cfg.asStateFlow()

    private val _width = MutableStateFlow(512)
    val width = _width.asStateFlow()

    private val _height = MutableStateFlow(512)
    val height = _height.asStateFlow()

    private val _seed = MutableStateFlow(-1L) // -1 means random/not set manually
    val seed = _seed.asStateFlow()

    private val _isSeedRandom = MutableStateFlow(true)
    val isSeedRandom = _isSeedRandom.asStateFlow()

    private val _sampler = MutableStateFlow("euler")
    val sampler = _sampler.asStateFlow()

    // Server object lists (for dropdowns)
    private val _availableSamplers = MutableStateFlow<List<String>>(
        listOf("euler", "euler_ancestral", "heun", "dpm_2", "dpm_2_ancestral", "lms", "dpm_fast", "dpm_adaptive", "ddim", "uni_pc")
    )
    val availableSamplers = _availableSamplers.asStateFlow()

    // Connection Ping Result
    private val _connectionResult = MutableStateFlow<String?>(null)
    val connectionResult = _connectionResult.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection = _isTestingConnection.asStateFlow()

    // Gallery options
    private val _galleryGridColumns = MutableStateFlow(2)
    val galleryGridColumns = _galleryGridColumns.asStateFlow()

    private val _gallerySortOrder = MutableStateFlow("newest") // "newest", "oldest"
    val gallerySortOrder = _gallerySortOrder.asStateFlow()

    private val _galleryFilterWorkflow = MutableStateFlow<String?>(null)
    val galleryFilterWorkflow = _galleryFilterWorkflow.asStateFlow()

    private val _selectedGalleryImages = MutableStateFlow<Set<GeneratedImage>>(emptySet())
    val selectedGalleryImages = _selectedGalleryImages.asStateFlow()

    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode = _isMultiSelectMode.asStateFlow()

    // Active connection statuses
    val connectionStatus = comfyClient.connectionStatus
    val generationStatus = comfyClient.generationStatus
    val currentStep = comfyClient.currentStep
    val totalSteps = comfyClient.totalSteps
    val previewBitmap = comfyClient.previewBitmap
    val isGenerating = comfyClient.isGenerating
    val queueRemaining = comfyClient.queueRemaining

    // Database access
    val allImages = repository.allImages
    val allPresets = repository.allPresets
    val promptHistory = repository.promptHistory
    val favoritePrompts = repository.favoritePrompts
    val lastGeneratedImage = repository.lastGeneratedImage
    val generationError = repository.generationError

    // Current active workflow preset loaded
    private val _activeWorkflowId = MutableStateFlow<Int?>(null)
    val activeWorkflowId = _activeWorkflowId.asStateFlow()

    private val defaultWorkflowTemplate = """
    {
      "3": {
        "inputs": {
          "seed": 12345,
          "steps": 20,
          "cfg": 7.0,
          "sampler_name": "euler",
          "scheduler": "normal",
          "denoise": 1.0,
          "model": ["4", 0],
          "positive": ["6", 0],
          "negative": ["7", 0],
          "latent_image": ["5", 0]
        },
        "class_type": "KSampler"
      },
      "4": {
        "inputs": {
          "ckpt_name": "v1-5-pruned-emaonly.safetensors"
        },
        "class_type": "CheckpointLoaderSimple"
      },
      "5": {
        "inputs": {
          "width": 512,
          "height": 512,
          "batch_size": 1
        },
        "class_type": "EmptyLatentImage"
      },
      "6": {
        "inputs": {
          "text": "",
          "clip": ["4", 1]
        },
        "class_type": "CLIPTextEncode"
      },
      "7": {
        "inputs": {
          "text": "",
          "clip": ["4", 1]
        },
        "class_type": "CLIPTextEncode"
      },
      "8": {
        "inputs": {
          "samples": ["3", 0],
          "vae": ["4", 2]
        },
        "class_type": "VAEDecode"
      },
      "9": {
        "inputs": {
          "filename_prefix": "ComfyPad",
          "images": ["8", 0]
        },
        "class_type": "SaveImage"
      }
    }
    """.trimIndent()

    init {
        // Preset values from SharedPreferences
        _steps.value = settingsManager.defaultSteps
        _cfg.value = settingsManager.defaultCfg
        _width.value = settingsManager.defaultWidth
        _height.value = settingsManager.defaultHeight

        // Connect automatically if server is configured
        if (settingsManager.serverIp.isNotEmpty()) {
            comfyClient.connectWebSocket()
            fetchSamplers()
        }

        // Initialize default presets in db if empty
        viewModelScope.launch {
            repository.allPresets.first().let { presets ->
                if (presets.isEmpty()) {
                    repository.insertWorkflow(
                        WorkflowPreset(
                            name = "Standard SD Text-to-Image",
                            jsonContent = defaultWorkflowTemplate,
                            isDefault = true
                        )
                    )
                }
            }
        }
    }

    fun setServerConfig(ip: String, port: Int) {
        settingsManager.serverIp = ip
        settingsManager.serverPort = port
        comfyClient.connectWebSocket()
        fetchSamplers()
    }

    fun testConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionResult.value = null
            val result = comfyClient.testConnection()
            _connectionResult.value = result
            _isTestingConnection.value = false
        }
    }

    fun fetchSamplers() {
        viewModelScope.launch {
            try {
                val info = comfyClient.getObjectInfo()
                if (info != null) {
                    val ksamplerObj = info.optJSONObject("KSampler")
                    val inputObj = ksamplerObj?.optJSONObject("input")
                    val requiredObj = inputObj?.optJSONObject("required")
                    val samplerNameArray = requiredObj?.optJSONArray("sampler_name")
                    if (samplerNameArray != null && samplerNameArray.optJSONArray(0) != null) {
                        val firstInner = samplerNameArray.getJSONArray(0)
                        val samplerList = mutableListOf<String>()
                        for (i in 0 until firstInner.length()) {
                            samplerList.add(firstInner.getString(i))
                        }
                        _availableSamplers.value = samplerList
                    } else if (samplerNameArray != null) {
                        val samplerList = mutableListOf<String>()
                        for (i in 0 until samplerNameArray.length()) {
                            samplerList.add(samplerNameArray.getString(i))
                        }
                        _availableSamplers.value = samplerList
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching samplers from server info", e)
            }
        }
    }

    // Setters
    fun updatePositivePrompt(p: String) { _positivePrompt.value = p }
    fun updateNegativePrompt(n: String) { _negativePrompt.value = n }
    fun updateSteps(s: Int) { _steps.value = s }
    fun updateCfg(c: Float) { _cfg.value = c }
    fun updateWidth(w: Int) { _width.value = w }
    fun updateHeight(h: Int) { _height.value = h }
    fun updateSeed(s: Long) { _seed.value = s }
    fun setUserSeedRandom(r: Boolean) { _isSeedRandom.value = r }
    fun updateSampler(s: String) { _sampler.value = s }

    // Style helper tags
    val quickStyleTags = listOf(
        "cinematic", "4K", "dark moody", "anime", "photorealistic", 
        "watercolor", "cyberpunk", "vintage portrait", "masterpiece", "epic detail"
    )

    fun appendStyleTag(tag: String) {
        val current = _positivePrompt.value
        if (current.isEmpty()) {
            _positivePrompt.value = tag
        } else {
            _positivePrompt.value = if (current.trim().endsWith(",")) {
                "$current $tag"
            } else {
                "$current, $tag"
            }
        }
    }

    // Generation Trigger
    fun triggerGeneration() {
        viewModelScope.launch {
            // Find template content
            val templateJson = if (_activeWorkflowId.value != null) {
                val preset = repository.allPresets.first().find { it.id == _activeWorkflowId.value }
                preset?.jsonContent ?: defaultWorkflowTemplate
            } else {
                defaultWorkflowTemplate
            }

            val finalSeed = if (_isSeedRandom.value) {
                (0..Long.MAX_VALUE).random()
            } else {
                if (_seed.value < 0) 123456L else _seed.value
            }

            val finalJson = repository.modifyWorkflowJson(
                templateJson = templateJson,
                positivePrompt = _positivePrompt.value,
                negativePrompt = _negativePrompt.value,
                seed = finalSeed,
                steps = _steps.value,
                cfg = _cfg.value,
                sampler = _sampler.value,
                width = _width.value,
                height = _height.value
            )

            comfyClient.queuePrompt(finalJson)
        }
    }

    fun cancelGeneration() {
        viewModelScope.launch {
            comfyClient.cancelGeneration()
        }
    }

    // Workflows management
    fun loadWorkflow(preset: WorkflowPreset) {
        _activeWorkflowId.value = preset.id
        viewModelScope.launch {
            try {
                // Parse properties from loaded JSON if possible to populate Generate UI
                val root = JSONObject(preset.jsonContent)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val node = root.optJSONObject(key) ?: continue
                    val classType = node.optString("class_type")
                    val inputs = node.optJSONObject("inputs") ?: continue

                    when (classType) {
                        "KSampler" -> {
                            _steps.value = inputs.optInt("steps", _steps.value)
                            _cfg.value = inputs.optDouble("cfg", _cfg.value.toDouble()).toFloat()
                            val loadedSeed = inputs.optLong("seed")
                            _seed.value = loadedSeed
                            _isSeedRandom.value = false
                            _sampler.value = inputs.optString("sampler_name", _sampler.value)
                        }
                        "EmptyLatentImage" -> {
                            _width.value = inputs.optInt("width", _width.value)
                            _height.value = inputs.optInt("height", _height.value)
                        }
                        "CLIPTextEncode" -> {
                            val text = inputs.optString("text")
                            // Decide if positive/negative.
                            // If primarySampler has links, let's map. But for simple parsing:
                            // We can search if positive or negative based on KSampler connector.
                            // Let's check:
                            val ksamplers = mutableListOf<JSONObject>()
                            val kkeys = root.keys()
                            while (kkeys.hasNext()) {
                                val ki = kkeys.next()
                                val kn = root.optJSONObject(ki) ?: continue
                                if (kn.optString("class_type") == "KSampler") {
                                    ksamplers.add(kn)
                                }
                            }
                            if (ksamplers.isNotEmpty()) {
                                val ks = ksamplers[0]
                                val ksIn = ks.optJSONObject("inputs")
                                val posId = ksIn?.optJSONArray("positive")?.optString(0)
                                val negId = ksIn?.optJSONArray("negative")?.optString(0)

                                if (key == posId) {
                                    _positivePrompt.value = text
                                } else if (key == negId) {
                                    _negativePrompt.value = text
                                }
                            } else {
                                if (_positivePrompt.value.isEmpty()) {
                                    _positivePrompt.value = text
                                } else if (_negativePrompt.value.isEmpty()) {
                                    _negativePrompt.value = text
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error populating load workflow fields", e)
            }
        }
    }

    fun saveCurrentAsWorkflowPreset(name: String) {
        viewModelScope.launch {
            val templateJson = if (_activeWorkflowId.value != null) {
                val preset = repository.allPresets.first().find { it.id == _activeWorkflowId.value }
                preset?.jsonContent ?: defaultWorkflowTemplate
            } else {
                defaultWorkflowTemplate
            }

            val finalSeed = if (_isSeedRandom.value) {
                12345L
            } else {
                if (_seed.value < 0) 12345L else _seed.value
            }

            val presetJson = repository.modifyWorkflowJson(
                templateJson = templateJson,
                positivePrompt = _positivePrompt.value,
                negativePrompt = _negativePrompt.value,
                seed = finalSeed,
                steps = _steps.value,
                cfg = _cfg.value,
                sampler = _sampler.value,
                width = _width.value,
                height = _height.value
            )

            val preset = WorkflowPreset(
                name = name,
                jsonContent = presetJson,
                dateModified = System.currentTimeMillis()
            )
            repository.insertWorkflow(preset)
        }
    }

    fun deletePreset(preset: WorkflowPreset) {
        viewModelScope.launch {
            repository.deleteWorkflowById(preset.id)
            if (_activeWorkflowId.value == preset.id) {
                _activeWorkflowId.value = null
            }
        }
    }

    fun updatePresetName(preset: WorkflowPreset, newName: String) {
        viewModelScope.launch {
            repository.updateWorkflow(preset.copy(name = newName, dateModified = System.currentTimeMillis()))
        }
    }

    fun importWorkflowPreset(context: Context, uri: Uri, name: String) {
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val jsonStr = reader.use { it.readText() }
                    // Verify if valid JSON
                    JSONObject(jsonStr)

                    repository.insertWorkflow(
                        WorkflowPreset(
                            name = name,
                            jsonContent = jsonStr,
                            dateModified = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import workflow", e)
            }
        }
    }

    // Gallery & Room state modifiers
    fun toggleFavorite(image: GeneratedImage) {
        viewModelScope.launch {
            val updated = image.copy(isFavorite = !image.isFavorite)
            repository.updateImage(updated)
            // also pin to favorite prompts if favourited
            if (updated.isFavorite) {
                repository.toggleFavoritePrompt(updated.prompt, true)
            }
        }
    }

    fun toggleFavoritePrompt(prompt: String, isFav: Boolean) {
        viewModelScope.launch {
            repository.toggleFavoritePrompt(prompt, isFav)
        }
    }

    fun deleteImage(image: GeneratedImage) {
        viewModelScope.launch {
            repository.deleteImage(image)
        }
    }

    fun setGalleryGridColumns(cols: Int) {
        _galleryGridColumns.value = cols
    }

    fun setGallerySortOrder(order: String) {
        _gallerySortOrder.value = order
    }

    fun setGalleryFilterWorkflow(wName: String?) {
        _galleryFilterWorkflow.value = wName
    }

    // Bulk selection managers
    fun toggleSelectImage(image: GeneratedImage) {
        val currentSet = _selectedGalleryImages.value.toMutableSet()
        if (currentSet.contains(image)) {
            currentSet.remove(image)
        } else {
            currentSet.add(image)
        }
        _selectedGalleryImages.value = currentSet
        if (currentSet.isEmpty()) {
            _isMultiSelectMode.value = false
        }
    }

    fun startMultiSelectMode(image: GeneratedImage) {
        _isMultiSelectMode.value = true
        _selectedGalleryImages.value = setOf(image)
    }

    fun exitMultiSelectMode() {
        _isMultiSelectMode.value = false
        _selectedGalleryImages.value = emptySet()
    }

    fun deleteSelectedImages() {
        viewModelScope.launch {
            repository.deleteBulkImages(_selectedGalleryImages.value.toList())
            exitMultiSelectMode()
        }
    }

    // Load back settings from past image
    fun loadSettingsFromImage(image: GeneratedImage) {
        _positivePrompt.value = image.prompt
        _negativePrompt.value = image.negativePrompt ?: ""
        _steps.value = image.steps
        _cfg.value = image.cfg
        _width.value = image.width
        _height.value = image.height
        _seed.value = image.seed
        _isSeedRandom.value = false
        _sampler.value = image.sampler
    }

    fun clearCache() {
        repository.clearCache()
    }
}
