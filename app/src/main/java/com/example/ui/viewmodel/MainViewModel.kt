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
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

import java.io.File

data class SelectedLora(
    val name: String,
    val strength: Float
)

enum class ModelType {
    FLUX, SDXL, TURBO, SD15
}

enum class UnifiedModelType {
    GGUF, FLUX, SDXL, SD15
}

enum class FluxType {
    DEV, SCHNELL
}

data class ComfyModel(
    val name: String,
    val type: UnifiedModelType
)

data class ComfyAssets(
    val checkpoints: List<String> = emptyList(),
    val loras: List<String> = emptyList(),
    val vaes: List<String> = emptyList(),
    val samplers: List<String> = emptyList(),
    val schedulers: List<String> = emptyList(),
    val controlNets: List<String> = emptyList(),
    val upscaleModels: List<String> = emptyList(),
    val embeddings: List<String> = emptyList(),
    val fluxSafetensors: List<String> = emptyList(),
    val fluxGgufs: List<String> = emptyList(),
    val clips: List<String> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    private val context = application.applicationContext

    // Dependencies
    val settingsManager = SettingsManager(context)
    val database = AppDatabase.getDatabase(context)
    val comfyClient = ComfyClient(settingsManager, viewModelScope)
    val repository = ComfyRepository(context, comfyClient, database, settingsManager, viewModelScope)

    private var cachedObjectInfo: org.json.JSONObject? = null

    private fun castValueToExpectedType(nodeType: String, fieldName: String, rawValue: Any?): Any? {
        val info = cachedObjectInfo ?: return rawValue
        val nodeObj = info.optJSONObject(nodeType) ?: return rawValue
        val inputObj = nodeObj.optJSONObject("input") ?: return rawValue
        val requiredObj = inputObj.optJSONObject("required")
        val optionalObj = inputObj.optJSONObject("optional")
        val fieldSpec = requiredObj?.optJSONArray(fieldName) ?: optionalObj?.optJSONArray(fieldName) ?: return rawValue

        val typeIndicator = fieldSpec.opt(0)
        if (typeIndicator is org.json.JSONArray) {
            if (typeIndicator.length() > 0) {
                val firstVal = typeIndicator.opt(0)
                if (firstVal is Number) {
                    val str = rawValue?.toString() ?: "0"
                    val numVal = str.toDoubleOrNull() ?: 0.0
                    return if (firstVal is Double || firstVal is Float) {
                        numVal
                    } else {
                        numVal.toInt()
                    }
                } else {
                    return rawValue?.toString()
                }
            }
        } else if (typeIndicator is String) {
            val typeStr = typeIndicator.uppercase()
            val str = rawValue?.toString() ?: ""
            if (typeStr == "INT" || typeStr == "INTEGER") {
                return str.toDoubleOrNull()?.toInt() ?: 0
            }
            if (typeStr == "FLOAT" || typeStr == "NUMBER") {
                return str.toDoubleOrNull() ?: 0.0
            }
            if (typeStr == "BOOLEAN" || typeStr == "BOOL") {
                return str.lowercase() == "true" || str == "1"
            }
            if (typeStr == "STRING") {
                return str
            }
        }
        return rawValue
    }

    private fun sanitizeWorkflowTypes(workflow: org.json.JSONObject): org.json.JSONObject {
        try {
            val keys = workflow.keys()
            while (keys.hasNext()) {
                val nodeId = keys.next()
                val nodeObj = workflow.optJSONObject(nodeId) ?: continue
                val classType = nodeObj.optString("class_type") ?: continue
                val inputsObj = nodeObj.optJSONObject("inputs") ?: continue

                val inputKeys = inputsObj.keys()
                val fieldsToUpdate = mutableListOf<Pair<String, Any?>>()
                while (inputKeys.hasNext()) {
                    val fieldName = inputKeys.next()
                    val rawValue = inputsObj.opt(fieldName)
                    if (rawValue is org.json.JSONArray) {
                        continue
                    }
                    val castedValue = castValueToExpectedType(classType, fieldName, rawValue)
                    if (castedValue != rawValue) {
                        fieldsToUpdate.add(Pair(fieldName, castedValue))
                    }
                }

                for ((field, value) in fieldsToUpdate) {
                    inputsObj.put(field, value)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sanitizing workflow types", e)
        }
        return workflow
    }

    // Dynamic assets cache states
    private val _assetsLoading = MutableStateFlow(false)
    val assetsLoading = _assetsLoading.asStateFlow()

    private val _assetsError = MutableStateFlow<String?>(null)
    val assetsError = _assetsError.asStateFlow()

    private val _assets = MutableStateFlow(ComfyAssets())
    val assets = _assets.asStateFlow()

    // Persistent Selection states
    private val _selectedCheckpoint = MutableStateFlow<String?>(settingsManager.selectedCheckpoint)
    val selectedCheckpoint = _selectedCheckpoint.asStateFlow()

    private val _selectedClip1 = MutableStateFlow(settingsManager.selectedClip1)
    val selectedClip1 = _selectedClip1.asStateFlow()

    private val _selectedClip2 = MutableStateFlow(settingsManager.selectedClip2)
    val selectedClip2 = _selectedClip2.asStateFlow()

    val modelType: StateFlow<ModelType> = _selectedCheckpoint
        .map { checkpoint ->
            val name = checkpoint?.lowercase() ?: ""
            when {
                name.contains("flux") || name.contains("gguf") -> ModelType.FLUX
                name.contains("turbo") || name.contains("lightning") || name.contains("hyper") -> ModelType.TURBO
                name.contains("xl") || name.contains("sdxl") || name.contains("pony") || name.contains("illustrious") -> ModelType.SDXL
                else -> ModelType.SD15
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModelType.SD15)

    // Sub-type for Flux (DEV or SCHNELL)
    val fluxType: StateFlow<FluxType> = _selectedCheckpoint
        .map { checkpoint ->
            val name = checkpoint?.lowercase() ?: ""
            if (name.contains("schnell")) FluxType.SCHNELL else FluxType.DEV
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FluxType.DEV)

    val unifiedModels: StateFlow<List<ComfyModel>> = _assets
        .map { assets_inst ->
            val list = mutableListOf<ComfyModel>()
            
            // UnetLoaderGGUF options
            assets_inst.fluxGgufs.forEach { name ->
                list.add(ComfyModel(name, UnifiedModelType.GGUF))
            }
            
            // UNETLoader options
            assets_inst.fluxSafetensors.forEach { name ->
                list.add(ComfyModel(name, UnifiedModelType.FLUX))
            }
            
            // CheckpointLoaderSimple options
            assets_inst.checkpoints.forEach { name ->
                val lower = name.lowercase()
                when {
                    lower.contains("flux") -> list.add(ComfyModel(name, UnifiedModelType.FLUX))
                    lower.contains("xl") || lower.contains("sdxl") || lower.contains("pony") || lower.contains("illustrious") -> {
                        list.add(ComfyModel(name, UnifiedModelType.SDXL))
                    }
                    else -> list.add(ComfyModel(name, UnifiedModelType.SD15))
                }
            }
            
            list.distinctBy { it.name }.sortedBy { it.name }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun getSelectedModelType(): UnifiedModelType {
        val current = _selectedCheckpoint.value ?: ""
        val models = unifiedModels.value
        val found = models.find { it.name == current }
        if (found != null) return found.type
        
        val lower = current.lowercase()
        return when {
            lower.endsWith(".gguf") || lower.contains("gguf") -> UnifiedModelType.GGUF
            lower.contains("flux") -> UnifiedModelType.FLUX
            lower.contains("xl") || lower.contains("sdxl") || lower.contains("pony") || lower.contains("illustrious") -> UnifiedModelType.SDXL
            else -> UnifiedModelType.SD15
        }
    }

    fun updateSelectedClip1(clip: String) {
        _selectedClip1.value = clip
        settingsManager.selectedClip1 = clip
    }

    fun updateSelectedClip2(clip: String) {
        _selectedClip2.value = clip
        settingsManager.selectedClip2 = clip
    }

    private val _selectedVae = MutableStateFlow(settingsManager.selectedVae)
    val selectedVae = _selectedVae.asStateFlow()

    private val _selectedScheduler = MutableStateFlow(settingsManager.selectedScheduler)
    val selectedScheduler = _selectedScheduler.asStateFlow()

    private val _clipSkip = MutableStateFlow(settingsManager.clipSkip)
    val clipSkip = _clipSkip.asStateFlow()

    private val _hiresEnabled = MutableStateFlow(settingsManager.hiresEnabled)
    val hiresEnabled = _hiresEnabled.asStateFlow()

    private val _hiresUpscaler = MutableStateFlow<String?>(settingsManager.hiresUpscaler)
    val hiresUpscaler = _hiresUpscaler.asStateFlow()

    private val _hiresScale = MutableStateFlow(settingsManager.hiresScale)
    val hiresScale = _hiresScale.asStateFlow()

    private val _hiresSteps = MutableStateFlow(settingsManager.hiresSteps)
    val hiresSteps = _hiresSteps.asStateFlow()

    private val _hiresDenoise = MutableStateFlow(settingsManager.hiresDenoise)
    val hiresDenoise = _hiresDenoise.asStateFlow()

    private val _batchCount = MutableStateFlow(settingsManager.batchCount)
    val batchCount = _batchCount.asStateFlow()

    private val _selectedLoras = MutableStateFlow<List<SelectedLora>>(emptyList())
    val selectedLoras = _selectedLoras.asStateFlow()

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

    private val _sampler = MutableStateFlow(settingsManager.selectedSampler)
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

    // Loaded workflow state for Dynamic Workflow UI Generator
    private val _loadedWorkflow = MutableStateFlow<LoadedWorkflow?>(null)
    val loadedWorkflow = _loadedWorkflow.asStateFlow()

    private val _serverWorkflows = MutableStateFlow<List<String>>(emptyList())
    val serverWorkflows = _serverWorkflows.asStateFlow()

    private val _isFetchingServerWorkflows = MutableStateFlow(false)
    val isFetchingServerWorkflows = _isFetchingServerWorkflows.asStateFlow()

    private val _serverSyncError = MutableStateFlow<String?>(null)
    val serverSyncError = _serverSyncError.asStateFlow()

    // Gallery options
    private val _galleryGridColumns = MutableStateFlow(2)
    val galleryGridColumns = _galleryGridColumns.asStateFlow()

    private val _gallerySortOrder = MutableStateFlow("newest") // "newest", "oldest"
    val gallerySortOrder = _gallerySortOrder.asStateFlow()

    private val _galleryFilterWorkflow = MutableStateFlow<String?>(null)
    val galleryFilterWorkflow = _galleryFilterWorkflow.asStateFlow()

    // Generation-tab Face Swap persistent options from SettingsManager
    private val _genFaceSwapEnabled = MutableStateFlow(settingsManager.genFaceSwapEnabled)
    val genFaceSwapEnabled = _genFaceSwapEnabled.asStateFlow()

    private val _genFaceSwapSourceFaceUri = MutableStateFlow<Uri?>(
        if (settingsManager.genFaceSwapSourceFaceUri.isNotEmpty()) Uri.parse(settingsManager.genFaceSwapSourceFaceUri) else null
    )
    val genFaceSwapSourceFaceUri = _genFaceSwapSourceFaceUri.asStateFlow()

    private val _genFaceSwapRestoreModel = MutableStateFlow(settingsManager.genFaceSwapRestoreModel)
    val genFaceSwapRestoreModel = _genFaceSwapRestoreModel.asStateFlow()

    private val _genFaceSwapVisibility = MutableStateFlow(settingsManager.genFaceSwapVisibility)
    val genFaceSwapVisibility = _genFaceSwapVisibility.asStateFlow()

    private val _genFaceSwapWeight = MutableStateFlow(settingsManager.genFaceSwapWeight)
    val genFaceSwapWeight = _genFaceSwapWeight.asStateFlow()

    fun updateGenFaceSwapEnabled(enabled: Boolean) {
        _genFaceSwapEnabled.value = enabled
        settingsManager.genFaceSwapEnabled = enabled
    }

    private val _genFaceSwapSourceFilename = MutableStateFlow<String?>(null)
    val genFaceSwapSourceFilename = _genFaceSwapSourceFilename.asStateFlow()

    private val _genFaceSwapSourceUploading = MutableStateFlow(false)
    val genFaceSwapSourceUploading = _genFaceSwapSourceUploading.asStateFlow()

    private val _genFaceSwapSourceUploadError = MutableStateFlow<String?>(null)
    val genFaceSwapSourceUploadError = _genFaceSwapSourceUploadError.asStateFlow()

    fun updateGenFaceSwapSourceFaceUri(uri: Uri?) {
        _genFaceSwapSourceFaceUri.value = uri
        settingsManager.genFaceSwapSourceFaceUri = uri?.toString() ?: ""
        if (uri != null) {
            uploadGenFaceSwapSource(uri)
        } else {
            _genFaceSwapSourceFilename.value = null
            _genFaceSwapSourceUploadError.value = null
        }
    }

    fun uploadGenFaceSwapSource(uri: Uri) {
        viewModelScope.launch {
            _genFaceSwapSourceUploading.value = true
            _genFaceSwapSourceUploadError.value = null
            _genFaceSwapSourceFilename.value = null
            val filename = repository.uploadImageToComfyUI(uri)
            _genFaceSwapSourceUploading.value = false
            if (filename != null) {
                _genFaceSwapSourceFilename.value = filename
            } else {
                _genFaceSwapSourceUploadError.value = "Failed to upload face image to server"
            }
        }
    }

    fun updateGenFaceSwapRestoreModel(model: String) {
        _genFaceSwapRestoreModel.value = model
        settingsManager.genFaceSwapRestoreModel = model
    }

    fun updateGenFaceSwapVisibility(v: Float) {
        _genFaceSwapVisibility.value = v
        settingsManager.genFaceSwapVisibility = v
    }

    fun updateGenFaceSwapWeight(w: Float) {
        _genFaceSwapWeight.value = w
        settingsManager.genFaceSwapWeight = w
    }

    // Dynamic ReActor fields & options
    private val _reactorNodeInfo = MutableStateFlow<ReActorNodeInfo?>(null)
    val reactorNodeInfo = _reactorNodeInfo.asStateFlow()

    private val _reactorLoading = MutableStateFlow(false)
    val reactorLoading = _reactorLoading.asStateFlow()

    private val _reactorError = MutableStateFlow<String?>(null)
    val reactorError = _reactorError.asStateFlow()

    private val _reactorSelectedSwapModel = MutableStateFlow("inswapper_128.onnx")
    val reactorSelectedSwapModel = _reactorSelectedSwapModel.asStateFlow()

    private val _reactorSelectedFaceDetection = MutableStateFlow("retinaface_resnet50")
    val reactorSelectedFaceDetection = _reactorSelectedFaceDetection.asStateFlow()

    private val _reactorSelectedRestoreModel = MutableStateFlow("none")
    val reactorSelectedRestoreModel = _reactorSelectedRestoreModel.asStateFlow()

    private val _reactorSelectedGenderSource = MutableStateFlow("no")
    val reactorSelectedGenderSource = _reactorSelectedGenderSource.asStateFlow()

    private val _reactorSelectedGenderInput = MutableStateFlow("no")
    val reactorSelectedGenderInput = _reactorSelectedGenderInput.asStateFlow()

    private val _reactorRestoreVisibility = MutableStateFlow(1.0f)
    val reactorRestoreVisibility = _reactorRestoreVisibility.asStateFlow()

    private val _reactorCodeformerWeight = MutableStateFlow(0.5f)
    val reactorCodeformerWeight = _reactorCodeformerWeight.asStateFlow()

    private val _reactorSourceFacesIndex = MutableStateFlow("0")
    val reactorSourceFacesIndex = _reactorSourceFacesIndex.asStateFlow()

    private val _reactorInputFacesIndex = MutableStateFlow("0")
    val reactorInputFacesIndex = _reactorInputFacesIndex.asStateFlow()

    fun updateReactorSelectedSwapModel(value: String) { _reactorSelectedSwapModel.value = value }
    fun updateReactorSelectedFaceDetection(value: String) { _reactorSelectedFaceDetection.value = value }
    fun updateReactorSelectedRestoreModel(value: String) { _reactorSelectedRestoreModel.value = value }
    fun updateReactorSelectedGenderSource(value: String) { _reactorSelectedGenderSource.value = value }
    fun updateReactorSelectedGenderInput(value: String) { _reactorSelectedGenderInput.value = value }
    fun updateReactorRestoreVisibility(value: Float) { _reactorRestoreVisibility.value = value }
    fun updateReactorCodeformerWeight(value: Float) { _reactorCodeformerWeight.value = value }
    fun updateReactorSourceFacesIndex(value: String) { _reactorSourceFacesIndex.value = value }
    fun updateReactorInputFacesIndex(value: String) { _reactorInputFacesIndex.value = value }

    fun getFriendlyRestoreModelName(rawName: String): String {
        return when (rawName) {
            "none" -> "None"
            "codeformer-v0.1.0.pth" -> "CodeFormer"
            "GFPGANv1.3.pth" -> "GFPGAN v1.3"
            "GFPGANv1.4.pth" -> "GFPGAN v1.4"
            "GPEN-BFR-512.onnx" -> "GPEN"
            else -> rawName
        }
    }

    // Dedicated-tab Face Swap states (run locally / temporarily)
    private val _dediFaceSwapSourceUri = MutableStateFlow<Uri?>(null)
    val dediFaceSwapSourceUri = _dediFaceSwapSourceUri.asStateFlow()

    private val _dediFaceSwapTargetUri = MutableStateFlow<Uri?>(null)
    val dediFaceSwapTargetUri = _dediFaceSwapTargetUri.asStateFlow()

    private val _dediFaceSwapSourceFilename = MutableStateFlow<String?>(null)
    val dediFaceSwapSourceFilename = _dediFaceSwapSourceFilename.asStateFlow()

    private val _dediFaceSwapSourceUploading = MutableStateFlow(false)
    val dediFaceSwapSourceUploading = _dediFaceSwapSourceUploading.asStateFlow()

    private val _dediFaceSwapSourceUploadError = MutableStateFlow<String?>(null)
    val dediFaceSwapSourceUploadError = _dediFaceSwapSourceUploadError.asStateFlow()

    fun updateDediFaceSwapSourceUri(uri: Uri?) {
        _dediFaceSwapSourceUri.value = uri
        if (uri != null) {
            uploadDediFaceSwapSource(uri)
        } else {
            _dediFaceSwapSourceFilename.value = null
            _dediFaceSwapSourceUploadError.value = null
        }
    }

    fun uploadDediFaceSwapSource(uri: Uri) {
        viewModelScope.launch {
            _dediFaceSwapSourceUploading.value = true
            _dediFaceSwapSourceUploadError.value = null
            _dediFaceSwapSourceFilename.value = null
            val filename = repository.uploadImageToComfyUI(uri)
            _dediFaceSwapSourceUploading.value = false
            if (filename != null) {
                _dediFaceSwapSourceFilename.value = filename
            } else {
                _dediFaceSwapSourceUploadError.value = "Failed to upload source face image to server"
            }
        }
    }

    private val _dediFaceSwapTargetFilename = MutableStateFlow<String?>(null)
    val dediFaceSwapTargetFilename = _dediFaceSwapTargetFilename.asStateFlow()

    private val _dediFaceSwapTargetUploading = MutableStateFlow(false)
    val dediFaceSwapTargetUploading = _dediFaceSwapTargetUploading.asStateFlow()

    private val _dediFaceSwapTargetUploadError = MutableStateFlow<String?>(null)
    val dediFaceSwapTargetUploadError = _dediFaceSwapTargetUploadError.asStateFlow()

    fun updateDediFaceSwapTargetUri(uri: Uri?) {
        _dediFaceSwapTargetUri.value = uri
        if (uri != null) {
            uploadDediFaceSwapTarget(uri)
        } else {
            _dediFaceSwapTargetFilename.value = null
            _dediFaceSwapTargetUploadError.value = null
        }
    }

    fun uploadDediFaceSwapTarget(uri: Uri) {
        viewModelScope.launch {
            _dediFaceSwapTargetUploading.value = true
            _dediFaceSwapTargetUploadError.value = null
            _dediFaceSwapTargetFilename.value = null
            val filename = repository.uploadImageToComfyUI(uri)
            _dediFaceSwapTargetUploading.value = false
            if (filename != null) {
                _dediFaceSwapTargetFilename.value = filename
            } else {
                _dediFaceSwapTargetUploadError.value = "Failed to upload target image to server"
            }
        }
    }

    // Dedicated Tab - FaceFusion configurations
    private val _facefusionMode = MutableStateFlow("reference")
    val facefusionMode = _facefusionMode.asStateFlow()

    private val _facefusionDistance = MutableStateFlow(0.6f)
    val facefusionDistance = _facefusionDistance.asStateFlow()

    private val _facefusionEnhancer = MutableStateFlow("none")
    val facefusionEnhancer = _facefusionEnhancer.asStateFlow()

    private val _facefusionQuality = MutableStateFlow(80)
    val facefusionQuality = _facefusionQuality.asStateFlow()

    fun updateFaceFusionMode(m: String) {
        _facefusionMode.value = m
    }

    fun updateFaceFusionDistance(d: Float) {
        _facefusionDistance.value = d
    }

    fun updateFaceFusionEnhancer(e: String) {
        _facefusionEnhancer.value = e
    }

    fun updateFaceFusionQuality(q: Int) {
        _facefusionQuality.value = q
    }

    // Dedicated Face Swap state tracking
    private val _isSwapping = MutableStateFlow(false)
    val isSwapping = _isSwapping.asStateFlow()

    private val _swapResultImage = MutableStateFlow<GeneratedImage?>(null)
    val swapResultImage = _swapResultImage.asStateFlow()

    private val _swapError = MutableStateFlow<String?>(null)
    val swapError = _swapError.asStateFlow()

    fun clearSwapResult() {
        _swapResultImage.value = null
        _swapError.value = null
    }

    fun executeDedicatedFaceSwap(context: Context) {
        val sourceUri = _dediFaceSwapSourceUri.value
        val targetUri = _dediFaceSwapTargetUri.value
        if (sourceUri == null || targetUri == null) {
            _swapError.value = "Source and Target images must both be selected."
            return
        }

        viewModelScope.launch {
            _isSwapping.value = true
            _swapError.value = null
            _swapResultImage.value = null

            val engine = settingsManager.faceSwapEngine
            try {
                if (engine == "facefusion") {
                    // Execute using our custom FaceFusion REST API
                    val resultBytes = repository.executeFaceFusionSwap(sourceUri, targetUri)
                    if (resultBytes != null) {
                        val savedImage = repository.saveSwappedImage(
                            fileBytes = resultBytes,
                            engine = "FaceFusion",
                            sourceUri = sourceUri,
                            targetUri = targetUri
                        )
                        _swapResultImage.value = savedImage
                    } else {
                        _swapError.value = "FaceFusion server error. Please ensure external FaceFusion REST API is running and configured correctly in Settings."
                    }
                } else {
                    // ReActor node execution
                    var sourceFilename = _dediFaceSwapSourceFilename.value
                    if (sourceFilename == null) {
                        sourceFilename = repository.uploadImageToComfyUI(sourceUri)
                        if (sourceFilename == null) {
                            _swapError.value = "Failed to upload source face image to ComfyUI server."
                            _isSwapping.value = false
                            return@launch
                        }
                        _dediFaceSwapSourceFilename.value = sourceFilename
                    }

                    var targetFilename = _dediFaceSwapTargetFilename.value
                    if (targetFilename == null) {
                        targetFilename = repository.uploadImageToComfyUI(targetUri)
                        if (targetFilename == null) {
                            _swapError.value = "Failed to upload target image to ComfyUI server."
                            _isSwapping.value = false
                            return@launch
                        }
                        _dediFaceSwapTargetFilename.value = targetFilename
                    }

                    // Dynamically validate and update options if missing
                    try {
                        validateReActorAndPrepare()
                    } catch (e: Exception) {
                        _swapError.value = e.message ?: "Validation failed"
                        _isSwapping.value = false
                        return@launch
                    }

                    // Build ComfyUI workflow JSON
                    val flowObj = org.json.JSONObject()

                    // Load Source Image Node
                    val node100 = org.json.JSONObject()
                    val inputs100 = org.json.JSONObject()
                    inputs100.put("image", sourceFilename)
                    node100.put("inputs", inputs100)
                    node100.put("class_type", "LoadImage")
                    flowObj.put("100", node100)

                    // Load Target Image Node
                    val node101 = org.json.JSONObject()
                    val inputs101 = org.json.JSONObject()
                    inputs101.put("image", targetFilename)
                    node101.put("inputs", inputs101)
                    node101.put("class_type", "LoadImage")
                    flowObj.put("101", node101)

                    // ReActor Load Face Model Node
                    val node2 = org.json.JSONObject()
                    val inputs2 = org.json.JSONObject()
                    inputs2.put("face_model", _reactorSelectedSwapModel.value)
                    node2.put("inputs", inputs2)
                    node2.put("class_type", "ReActorLoadFaceModel")
                    flowObj.put("2", node2)

                    // ReActor Face Swap Node
                    val node3 = org.json.JSONObject()
                    val inputs3 = org.json.JSONObject()
                    inputs3.put("enabled", true)
                    inputs3.put("input_image", org.json.JSONArray().apply { put("101"); put(0) })
                    inputs3.put("source_image", org.json.JSONArray().apply { put("100"); put(0) })
                    inputs3.put("swap_model", _reactorSelectedSwapModel.value)
                    inputs3.put("facedetection", _reactorSelectedFaceDetection.value)
                    inputs3.put("face_restore_model", _reactorSelectedRestoreModel.value)
                    inputs3.put("face_restore_visibility", _reactorRestoreVisibility.value.toDouble())
                    inputs3.put("codeformer_weight", _reactorCodeformerWeight.value.toDouble())
                    inputs3.put("detect_gender_source", _reactorSelectedGenderSource.value)
                    inputs3.put("detect_gender_input", _reactorSelectedGenderInput.value)
                    inputs3.put("source_faces_index", _reactorSourceFacesIndex.value)
                    inputs3.put("input_faces_index", _reactorInputFacesIndex.value)
                    inputs3.put("console_log_level", (_reactorNodeInfo.value?.consoleLogLevelDefault ?: "1").toIntOrNull() ?: 1)
                    node3.put("inputs", inputs3)
                    node3.put("class_type", "ReActorFaceSwap")
                    flowObj.put("3", node3)

                    // Save Image Node
                    val node4 = org.json.JSONObject()
                    val inputs4 = org.json.JSONObject()
                    inputs4.put("images", org.json.JSONArray().apply { put("3"); put(0) })
                    inputs4.put("filename_prefix", "ComfyPad_FaceSwap")
                    node4.put("inputs", inputs4)
                    node4.put("class_type", "SaveImage")
                    flowObj.put("4", node4)

                    // Queue prompt and listen
                    val promptId = comfyClient.queuePrompt(sanitizeWorkflowTypes(flowObj).toString())
                    if (promptId != null) {
                        var checkAttempts = 0
                        var matchedImage: GeneratedImage? = null
                        while (checkAttempts < 60) {
                            delay(2000)
                            val latest = repository.lastGeneratedImage.value
                            if (latest != null && latest.originalImageRef == targetFilename) {
                                matchedImage = latest
                                break
                            }
                            if (comfyClient.generationStatus.value == com.example.data.network.GenerationStatus.SUCCESS && latest != null) {
                                matchedImage = latest
                                break
                            }
                            if (comfyClient.generationStatus.value == com.example.data.network.GenerationStatus.ERROR) {
                                _swapError.value = "ReActor workflow execution failed in ComfyUI."
                                break
                            }
                            checkAttempts++
                        }

                        if (matchedImage != null) {
                            _swapResultImage.value = matchedImage
                        } else if (_swapError.value == null) {
                            _swapError.value = "Face Swap execution timed out or failed on the ComfyUI server."
                        }
                    } else {
                        _swapError.value = "Failed to queue execution on the ComfyUI server. Ensure stable connection."
                    }
                }
            } catch (e: Exception) {
                _swapError.value = "Execution failed: ${e.message}"
            } finally {
                _isSwapping.value = false
            }
        }
    }

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

        // Parse saved LoRAs from preference
        try {
            val jsonArr = org.json.JSONArray(settingsManager.selectedLorasJson)
            val loraList = mutableListOf<SelectedLora>()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                loraList.add(SelectedLora(obj.getString("name"), obj.optDouble("strength", 0.8).toFloat()))
            }
            _selectedLoras.value = loraList
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing saved loras", e)
        }

        // Load offline cached assets first
        _assets.value = loadAssetsCache()

        // Connect automatically if server is configured
        if (settingsManager.serverIp.isNotEmpty()) {
            comfyClient.connectWebSocket()
            loadAssetsFromServer()
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

        // Trigger initial source face upload if saved URI is present
        val initialUri = if (settingsManager.genFaceSwapSourceFaceUri.isNotEmpty()) Uri.parse(settingsManager.genFaceSwapSourceFaceUri) else null
        if (initialUri != null) {
            uploadGenFaceSwapSource(initialUri)
        }
    }

    fun setServerConfig(ip: String, port: Int) {
        settingsManager.serverIp = ip
        settingsManager.serverPort = port
        comfyClient.connectWebSocket()
        loadAssetsFromServer()
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
        loadAssetsFromServer()
    }

    private fun parseComboOptions(array: org.json.JSONArray?): List<String> {
        if (array == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is org.json.JSONArray) {
                for (j in 0 until item.length()) {
                    list.add(item.getString(j))
                }
                if (list.isNotEmpty()) return list
            }
        }
        if (list.isEmpty()) {
            for (i in 0 until array.length()) {
                val s = array.optString(i)
                if (s != "combo" && s.isNotEmpty()) {
                    list.add(s)
                }
            }
        }
        return list
    }

    private fun extractOptions(objectInfo: org.json.JSONObject, nodeType: String, fieldName: String): List<String> {
        val nodeObj = objectInfo.optJSONObject(nodeType) ?: return emptyList()
        val inputObj = nodeObj.optJSONObject("input") ?: return emptyList()
        val requiredObj = inputObj.optJSONObject("required") ?: inputObj
        val fieldArray = requiredObj?.optJSONArray(fieldName) ?: return emptyList()
        return parseComboOptions(fieldArray)
    }

    private fun loadAssetsCache(): ComfyAssets {
        val json = settingsManager.assetsCacheJson
        if (json.isEmpty()) return ComfyAssets()
        return try {
            val obj = org.json.JSONObject(json)
            fun getList(key: String): List<String> {
                val arr = obj.optJSONArray(key) ?: return emptyList()
                return List(arr.length()) { arr.getString(it) }
            }
            ComfyAssets(
                checkpoints = getList("checkpoints"),
                loras = getList("loras"),
                vaes = getList("vaes"),
                samplers = getList("samplers"),
                schedulers = getList("schedulers"),
                controlNets = getList("controlNets"),
                upscaleModels = getList("upscaleModels"),
                embeddings = getList("embeddings"),
                fluxSafetensors = getList("fluxSafetensors"),
                fluxGgufs = getList("fluxGgufs"),
                clips = getList("clips")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached assets", e)
            ComfyAssets()
        }
    }

    private fun saveAssetsCache(assets: ComfyAssets) {
        try {
            val obj = org.json.JSONObject()
            fun putList(key: String, list: List<String>) {
                val arr = org.json.JSONArray(list)
                obj.put(key, arr)
            }
            putList("checkpoints", assets.checkpoints)
            putList("loras", assets.loras)
            putList("vaes", assets.vaes)
            putList("samplers", assets.samplers)
            putList("schedulers", assets.schedulers)
            putList("controlNets", assets.controlNets)
            putList("upscaleModels", assets.upscaleModels)
            putList("embeddings", assets.embeddings)
            putList("fluxSafetensors", assets.fluxSafetensors)
            putList("fluxGgufs", assets.fluxGgufs)
            putList("clips", assets.clips)
            settingsManager.assetsCacheJson = obj.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving assets cache", e)
        }
    }

    fun loadAssetsFromServer() {
        viewModelScope.launch {
            _assetsLoading.value = true
            _assetsError.value = null
            try {
                val info = comfyClient.getObjectInfo()
                if (info == null) {
                    _assetsError.value = "Failed to retrieve options from server"
                    _assetsLoading.value = false
                    return@launch
                }
                cachedObjectInfo = info
                
                // Fetch ReActor options using the loaded info to avoid extra server roundtrips
                fetchReActorOptions(info)

                val embeds = comfyClient.getEmbeddings()

                val ckpts = extractOptions(info, "CheckpointLoaderSimple", "ckpt_name").ifEmpty {
                    extractOptions(info, "CheckpointLoader", "ckpt_name")
                }
                
                // LoRAs from both loaders
                val loras1 = extractOptions(info, "LoraLoader", "lora_name")
                val loras2 = extractOptions(info, "LoraLoaderModelOnly", "lora_name")
                val loras = (loras1 + loras2).distinct().sorted()

                val vaes = extractOptions(info, "VAELoader", "vae_name")
                val samps = extractOptions(info, "KSampler", "sampler_name")
                val scheds = extractOptions(info, "KSampler", "scheduler")
                val cnets = extractOptions(info, "ControlNetLoader", "control_net_name")
                val upscales = extractOptions(info, "UpscaleModelLoader", "model_name")

                // Flux-specific models
                val fluxSafetensors = extractOptions(info, "UNETLoader", "unet_name")
                val fluxGgufs = extractOptions(info, "UnetLoaderGGUF", "unet_name")

                // CLIP models (CLIPLoader or DualCLIPLoader)
                val clips1 = extractOptions(info, "CLIPLoader", "clip_name")
                val clips2 = extractOptions(info, "DualCLIPLoader", "clip_name1")
                val clips = (clips1 + clips2).distinct().sorted()

                val newAssets = ComfyAssets(
                    checkpoints = ckpts.sorted(),
                    loras = loras,
                    vaes = vaes.sorted(),
                    samplers = samps.sorted(),
                    schedulers = scheds.sorted(),
                    controlNets = cnets.sorted(),
                    upscaleModels = upscales.sorted(),
                    embeddings = embeds.sorted(),
                    fluxSafetensors = fluxSafetensors.sorted(),
                    fluxGgufs = fluxGgufs.sorted(),
                    clips = clips
                )

                _assets.value = newAssets
                saveAssetsCache(newAssets)

                // Populate old fields as well
                _availableSamplers.value = samps

                if (_selectedCheckpoint.value == null && ckpts.isNotEmpty()) {
                    updateSelectedCheckpoint(ckpts[0])
                }
                if (_hiresUpscaler.value == null && upscales.isNotEmpty()) {
                    updateHiresUpscaler(upscales[0])
                }

                _assetsError.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching elements", e)
                _assetsError.value = e.message ?: "Unknown server asset loading error"
            } finally {
                _assetsLoading.value = false
            }
        }
    }

    fun fetchReActorOptions(providedObjectInfo: org.json.JSONObject? = null) {
        viewModelScope.launch {
            _reactorLoading.value = true
            _reactorError.value = null
            try {
                val objectInfo = providedObjectInfo ?: comfyClient.getObjectInfo()
                if (objectInfo == null) {
                    _reactorError.value = "Failed to connect to server and fetch object info."
                    _reactorNodeInfo.value = ReActorNodeInfo(isAvailable = false)
                    // Disable face swap toggle if reactor engine selected
                    if (settingsManager.faceSwapEngine == "reactor") {
                        _genFaceSwapEnabled.value = false
                        settingsManager.genFaceSwapEnabled = false
                    }
                    return@launch
                }
                cachedObjectInfo = objectInfo

                if (!objectInfo.has("ReActorFaceSwap")) {
                    _reactorError.value = "ReActor extension not found on server. Please install ComfyUI-ReActor and restart ComfyUI."
                    _reactorNodeInfo.value = ReActorNodeInfo(isAvailable = false)
                    if (settingsManager.faceSwapEngine == "reactor") {
                        _genFaceSwapEnabled.value = false
                        settingsManager.genFaceSwapEnabled = false
                    }
                    return@launch
                }

                val reactorNodeObj = objectInfo.getJSONObject("ReActorFaceSwap")
                val inputObj = reactorNodeObj.optJSONObject("input")
                val requiredObj = inputObj?.optJSONObject("required")

                if (requiredObj == null) {
                    _reactorError.value = "ReActor input format is unexpected/invalid."
                    _reactorNodeInfo.value = ReActorNodeInfo(isAvailable = false)
                    return@launch
                }

                val swapModels = jsonArrayToStringList(requiredObj.optJSONArray("swap_model"))
                val facedetections = jsonArrayToStringList(requiredObj.optJSONArray("facedetection"))
                val faceRestoreModels = jsonArrayToStringList(requiredObj.optJSONArray("face_restore_model"))
                val detectGenderSources = jsonArrayToStringList(requiredObj.optJSONArray("detect_gender_source"))
                val detectGenderInputs = jsonArrayToStringList(requiredObj.optJSONArray("detect_gender_input"))

                // Numeric slider attributes (face_restore_visibility)
                val visibilityMeta = requiredObj.optJSONArray("face_restore_visibility")
                val visMin = getNestedNumeric(visibilityMeta, "min", 0.0f)
                val visMax = getNestedNumeric(visibilityMeta, "max", 1.0f)
                val visDef = getNestedNumeric(visibilityMeta, "default", 1.0f)

                // Numeric slider attributes (codeformer_weight)
                val cfMeta = requiredObj.optJSONArray("codeformer_weight")
                val cfMin = getNestedNumeric(cfMeta, "min", 0.0f)
                val cfMax = getNestedNumeric(cfMeta, "max", 1.0f)
                val cfDef = getNestedNumeric(cfMeta, "default", 0.5f)

                val logLevels = jsonArrayToStringList(requiredObj.optJSONArray("console_log_level"))
                val consoleLogLevelDefault = logLevels.firstOrNull() ?: "1"

                val info = ReActorNodeInfo(
                    isAvailable = true,
                    swapModels = swapModels,
                    faceDetections = facedetections,
                    faceRestoreModels = faceRestoreModels,
                    detectGenderSources = detectGenderSources,
                    detectGenderInputs = detectGenderInputs,
                    faceRestoreVisibilityMin = visMin,
                    faceRestoreVisibilityMax = visMax,
                    faceRestoreVisibilityDefault = visDef,
                    codeformerWeightMin = cfMin,
                    codeformerWeightMax = cfMax,
                    codeformerWeightDefault = cfDef,
                    consoleLogLevelDefault = consoleLogLevelDefault,
                    swapModelDefault = swapModels.firstOrNull() ?: "",
                    facedetectionDefault = facedetections.firstOrNull() ?: "",
                    faceRestoreModelDefault = faceRestoreModels.firstOrNull() ?: "",
                    detectGenderSourceDefault = detectGenderSources.firstOrNull() ?: "",
                    detectGenderInputDefault = detectGenderInputs.firstOrNull() ?: ""
                )

                _reactorNodeInfo.value = info
                _reactorError.value = null

                // Fallbacks: populate state flows with defaults if currently unselected or invalid
                if (!_reactorSelectedSwapModel.value.let { swapModels.contains(it) }) {
                    _reactorSelectedSwapModel.value = info.swapModelDefault
                }
                if (!_reactorSelectedFaceDetection.value.let { facedetections.contains(it) }) {
                    _reactorSelectedFaceDetection.value = info.facedetectionDefault
                }
                if (!_reactorSelectedRestoreModel.value.let { faceRestoreModels.contains(it) }) {
                    _reactorSelectedRestoreModel.value = info.faceRestoreModelDefault
                }
                if (!_reactorSelectedGenderSource.value.let { detectGenderSources.contains(it) }) {
                    _reactorSelectedGenderSource.value = info.detectGenderSourceDefault
                }
                if (!_reactorSelectedGenderInput.value.let { detectGenderInputs.contains(it) }) {
                    _reactorSelectedGenderInput.value = info.detectGenderInputDefault
                }

                // If user sliders are out of bounds
                if (_reactorRestoreVisibility.value < visMin || _reactorRestoreVisibility.value > visMax) {
                    _reactorRestoreVisibility.value = visDef
                }
                if (_reactorCodeformerWeight.value < cfMin || _reactorCodeformerWeight.value > cfMax) {
                    _reactorCodeformerWeight.value = cfDef
                }

            } catch (e: Exception) {
                Log.e(TAG, "Uncaught error parsing ReActor options from /object_info", e)
                _reactorError.value = "Error parsing server ReActor options: ${e.message}"
                _reactorNodeInfo.value = ReActorNodeInfo(isAvailable = false)
            } finally {
                _reactorLoading.value = false
            }
        }
    }

    private suspend fun validateReActorAndPrepare(): Boolean {
        // Ensure ReActor options are loaded
        var options = _reactorNodeInfo.value
        if (options == null || !options.isAvailable) {
            val fetchedInfo = comfyClient.getObjectInfo()
            if (fetchedInfo != null) {
                cachedObjectInfo = fetchedInfo
                fetchReActorOptions(fetchedInfo)
            }
            options = _reactorNodeInfo.value
        }

        if (options == null || !options.isAvailable) {
            throw IllegalStateException("ReActor extension not found on server. Please install ComfyUI-ReActor and restart ComfyUI.")
        }

        var needsRefetch = false

        val currentSwap = _reactorSelectedSwapModel.value
        val currentDetect = _reactorSelectedFaceDetection.value
        val currentRestore = _reactorSelectedRestoreModel.value
        val currentGenSrc = _reactorSelectedGenderSource.value
        val currentGenIn = _reactorSelectedGenderInput.value

        if (!options.swapModels.contains(currentSwap)) {
            Log.w(TAG, "Selected swap model '$currentSwap' not in options.")
            needsRefetch = true
        }
        if (!options.faceDetections.contains(currentDetect)) {
            Log.w(TAG, "Selected face detection '$currentDetect' not in options.")
            needsRefetch = true
        }
        if (!options.faceRestoreModels.contains(currentRestore)) {
            Log.w(TAG, "Selected face restore model '$currentRestore' not in options.")
            needsRefetch = true
        }
        if (!options.detectGenderSources.contains(currentGenSrc)) {
            Log.w(TAG, "Selected gender detection source '$currentGenSrc' not in options.")
            needsRefetch = true
        }
        if (!options.detectGenderInputs.contains(currentGenIn)) {
            Log.w(TAG, "Selected gender detection input '$currentGenIn' not in options.")
            needsRefetch = true
        }

        if (needsRefetch) {
            Log.i(TAG, "Some selected ReActor values are missing from cache. Re-fetching /object_info...")
            val fetchedInfo = comfyClient.getObjectInfo()
            if (fetchedInfo != null) {
                cachedObjectInfo = fetchedInfo
                fetchReActorOptions(fetchedInfo)
            }
            val updatedOptions = _reactorNodeInfo.value ?: return false

            if (!updatedOptions.swapModels.contains(_reactorSelectedSwapModel.value)) {
                _reactorSelectedSwapModel.value = updatedOptions.swapModelDefault
            }
            if (!updatedOptions.faceDetections.contains(_reactorSelectedFaceDetection.value)) {
                _reactorSelectedFaceDetection.value = updatedOptions.facedetectionDefault
            }
            if (!updatedOptions.faceRestoreModels.contains(_reactorSelectedRestoreModel.value)) {
                _reactorSelectedRestoreModel.value = updatedOptions.faceRestoreModelDefault
            }
            if (!updatedOptions.detectGenderSources.contains(_reactorSelectedGenderSource.value)) {
                _reactorSelectedGenderSource.value = updatedOptions.detectGenderSourceDefault
            }
            if (!updatedOptions.detectGenderInputs.contains(_reactorSelectedGenderInput.value)) {
                _reactorSelectedGenderInput.value = updatedOptions.detectGenderInputDefault
            }
        }

        return true
    }

    private fun jsonArrayToStringList(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        val firstVal = arr.opt(0)
        if (firstVal is org.json.JSONArray) {
            for (i in 0 until firstVal.length()) {
                list.add(firstVal.optString(i))
            }
        } else if (firstVal is String) {
            list.add(firstVal)
        }
        return list
    }

    private fun getNestedNumeric(arr: org.json.JSONArray?, key: String, fallback: Float): Float {
        if (arr == null || arr.length() < 2) return fallback
        val secondVal = arr.optJSONObject(1) ?: return fallback
        if (secondVal.has(key)) {
            return secondVal.optDouble(key, fallback.toDouble()).toFloat()
        }
        return fallback
    }

    fun updateSelectedCheckpoint(ckpt: String?) {
        _selectedCheckpoint.value = ckpt
        settingsManager.selectedCheckpoint = ckpt
        applyDefaultsForCheckpoint(ckpt)
    }

    fun applyDefaultsForCheckpoint(ckpt: String?) {
        val name = ckpt?.lowercase() ?: ""
        when {
            name.contains("flux") || name.contains("gguf") -> {
                val isSchnell = name.contains("schnell")
                _cfg.value = if (isSchnell) 1.0f else 3.5f
                _steps.value = if (isSchnell) 4 else 20
                _width.value = 1024
                _height.value = 1024
                _sampler.value = "euler"
                _selectedScheduler.value = "simple"
                _clipSkip.value = 1

                val asts = _assets.value
                val possibleClips = asts.clips
                if (possibleClips.isNotEmpty()) {
                    val defaultClip1 = possibleClips.find { it.lowercase().contains("t5xxl") }
                        ?: possibleClips.find { it.lowercase().contains("t5") }
                        ?: possibleClips[0]
                    val defaultClip2 = possibleClips.find { it.lowercase().contains("clip_l") }
                        ?: possibleClips.find { it.lowercase().contains("clip") }
                        ?: possibleClips[0]
                    updateSelectedClip1(defaultClip1)
                    updateSelectedClip2(defaultClip2)
                }

                if (asts.vaes.contains("ae.safetensors")) {
                    updateSelectedVae("ae.safetensors")
                } else if (asts.vaes.contains("ae")) {
                    updateSelectedVae(asts.vaes.find { it.lowercase().contains("ae") }!!)
                } else if (asts.vaes.isNotEmpty()) {
                    updateSelectedVae(asts.vaes[0])
                } else {
                    updateSelectedVae("ae.safetensors")
                }
            }
            name.contains("turbo") || name.contains("lightning") || name.contains("hyper") -> {
                _cfg.value = 1.0f
                _steps.value = 4
                _selectedScheduler.value = "sgm_uniform"
            }
            name.contains("xl") || name.contains("sdxl") || name.contains("pony") || name.contains("illustrious") -> {
                _cfg.value = 7.0f
                _steps.value = 25
                _width.value = 1024
                _height.value = 1024
                _sampler.value = "dpmpp_2m"
                _selectedScheduler.value = "karras"
                _clipSkip.value = 2
            }
            else -> {
                _cfg.value = 7.0f
                _steps.value = 20
                _width.value = 512
                _height.value = 512
                _sampler.value = "euler"
                _selectedScheduler.value = "karras"
                _clipSkip.value = 1
            }
        }
    }

    fun updateSelectedVae(vae: String) {
        _selectedVae.value = vae
        settingsManager.selectedVae = vae
    }

    fun updateSelectedSampler(sampler: String) {
        _sampler.value = sampler
        settingsManager.selectedSampler = sampler
    }

    fun updateSelectedScheduler(scheduler: String) {
        _selectedScheduler.value = scheduler
        settingsManager.selectedScheduler = scheduler
    }

    fun updateClipSkip(skip: Int) {
        _clipSkip.value = skip
        settingsManager.clipSkip = skip
    }

    fun updateHiresEnabled(enabled: Boolean) {
        _hiresEnabled.value = enabled
        settingsManager.hiresEnabled = enabled
    }

    fun updateHiresUpscaler(upscaler: String?) {
        _hiresUpscaler.value = upscaler
        settingsManager.hiresUpscaler = upscaler
    }

    fun updateHiresScale(scale: Float) {
        _hiresScale.value = scale
        settingsManager.hiresScale = scale
    }

    fun updateHiresSteps(steps: Int) {
        _hiresSteps.value = steps
        settingsManager.hiresSteps = steps
    }

    fun updateHiresDenoise(denoise: Float) {
        _hiresDenoise.value = denoise
        settingsManager.hiresDenoise = denoise
    }

    fun updateBatchCount(count: Int) {
        _batchCount.value = count
        settingsManager.batchCount = count
    }

    private fun saveLorasListToPrefs(list: List<SelectedLora>) {
        try {
            val arr = org.json.JSONArray()
            for (item in list) {
                val obj = org.json.JSONObject()
                obj.put("name", item.name)
                obj.put("strength", item.strength.toDouble())
                arr.put(obj)
            }
            settingsManager.selectedLorasJson = arr.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving loras list", e)
        }
    }

    fun addLora(name: String) {
        val current = _selectedLoras.value.toMutableList()
        if (current.none { it.name == name }) {
            current.add(SelectedLora(name, 0.8f))
            _selectedLoras.value = current
            saveLorasListToPrefs(current)
        }
    }

    fun removeLora(name: String) {
        val current = _selectedLoras.value.toMutableList()
        current.removeAll { it.name == name }
        _selectedLoras.value = current
        saveLorasListToPrefs(current)
    }

    fun updateLoraStrength(name: String, strength: Float) {
        val current = _selectedLoras.value.map {
            if (it.name == name) it.copy(strength = strength) else it
        }
        _selectedLoras.value = current
        saveLorasListToPrefs(current)
    }

    fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun buildDynamicWorkflowJson(finalSeed: Long): String {
        val root = org.json.JSONObject()
        val ckptName = _selectedCheckpoint.value ?: "v1-5-pruned-emaonly.safetensors"
        val mType = modelType.value
        val faceSwapActive = _genFaceSwapEnabled.value
        val savePrefix = if (faceSwapActive) "ComfyPad_FaceSwap" else "ComfyPad_Original"

        if (mType == ModelType.FLUX) {
            // FLUX PIPELINE
            // Node 1: Check GGUF vs Safetensors
            val isGguf = getSelectedModelType() == UnifiedModelType.GGUF
            val unetNode = org.json.JSONObject()
            val unetInputs = org.json.JSONObject()
            unetInputs.put("unet_name", ckptName)
            if (isGguf) {
                unetNode.put("class_type", "UnetLoaderGGUF")
            } else {
                unetInputs.put("weight_dtype", "fp8_e4m3fn")
                unetNode.put("class_type", "UNETLoader")
            }
            unetNode.put("inputs", unetInputs)
            root.put("1", unetNode)

            // Node 2: DualCLIPLoader
            val clipLoaderObj = org.json.JSONObject()
            val clipLoaderInputs = org.json.JSONObject()
            clipLoaderInputs.put("clip_name1", _selectedClip1.value)
            clipLoaderInputs.put("clip_name2", _selectedClip2.value)
            clipLoaderInputs.put("type", "flux")
            clipLoaderObj.put("inputs", clipLoaderInputs)
            clipLoaderObj.put("class_type", "DualCLIPLoader")
            root.put("2", clipLoaderObj)

            // Node 3: VAELoader
            val vaeLoaderObj = org.json.JSONObject()
            val vaeLoaderInputs = org.json.JSONObject()
            val useVae = _selectedVae.value != "Automatic"
            vaeLoaderInputs.put("vae_name", if (useVae) _selectedVae.value else "ae.safetensors")
            vaeLoaderObj.put("inputs", vaeLoaderInputs)
            vaeLoaderObj.put("class_type", "VAELoader")
            root.put("3", vaeLoaderObj)

            // LoRA Model Only Chaining (FLUX)
            var currentModelOutput = org.json.JSONArray().apply { put("1"); put(0) }
            val loraList = _selectedLoras.value
            loraList.forEachIndexed { index, lora ->
                val loraId = (50 + index).toString()
                val loraNode = org.json.JSONObject()
                val loraInputs = org.json.JSONObject()
                loraInputs.put("lora_name", lora.name)
                loraInputs.put("strength_model", lora.strength.toDouble())
                loraInputs.put("model", currentModelOutput)
                loraNode.put("inputs", loraInputs)
                loraNode.put("class_type", "LoraLoaderModelOnly")
                root.put(loraId, loraNode)

                currentModelOutput = org.json.JSONArray().apply { put(loraId); put(0) }
            }

            // Node 4: CLIPTextEncode
            val posNode = org.json.JSONObject()
            val posInputs = org.json.JSONObject()
            posInputs.put("text", _positivePrompt.value)
            posInputs.put("clip", org.json.JSONArray().apply { put("2"); put(0) })
            posNode.put("inputs", posInputs)
            posNode.put("class_type", "CLIPTextEncode")
            root.put("4", posNode)

            // Node 5: CLIPTextEncode placeholder
            val negNode = org.json.JSONObject()
            val negInputs = org.json.JSONObject()
            negInputs.put("text", "")
            negInputs.put("clip", org.json.JSONArray().apply { put("2"); put(0) })
            negNode.put("inputs", negInputs)
            negNode.put("class_type", "CLIPTextEncode")
            root.put("5", negNode)

            // Node 6: EmptySD3LatentImage
            val latentNode = org.json.JSONObject()
            val latentInputs = org.json.JSONObject()
            latentInputs.put("width", _width.value)
            latentInputs.put("height", _height.value)
            latentInputs.put("batch_size", _batchCount.value)
            latentNode.put("inputs", latentInputs)
            latentNode.put("class_type", "EmptySD3LatentImage")
            root.put("6", latentNode)

            // Node 30: FluxGuidance (Guidance Scale)
            val guidanceNode = org.json.JSONObject()
            val guidanceInputs = org.json.JSONObject()
            guidanceInputs.put("guidance", _cfg.value.toDouble())
            guidanceInputs.put("conditioning", org.json.JSONArray().apply { put("4"); put(0) })
            guidanceNode.put("inputs", guidanceInputs)
            guidanceNode.put("class_type", "FluxGuidance")
            root.put("30", guidanceNode)

            // Node 7: BasicGuider
            val guiderNode = org.json.JSONObject()
            val guiderInputs = org.json.JSONObject()
            guiderInputs.put("model", currentModelOutput)
            guiderInputs.put("conditioning", org.json.JSONArray().apply { put("30"); put(0) })
            guiderNode.put("inputs", guiderInputs)
            guiderNode.put("class_type", "BasicGuider")
            root.put("7", guiderNode)

            // Node 8: BasicScheduler
            val schedulerNode = org.json.JSONObject()
            val schedulerInputs = org.json.JSONObject()
            schedulerInputs.put("model", currentModelOutput)
            schedulerInputs.put("scheduler", _selectedScheduler.value)
            schedulerInputs.put("steps", _steps.value)
            schedulerInputs.put("denoise", 1.0)
            schedulerNode.put("inputs", schedulerInputs)
            schedulerNode.put("class_type", "BasicScheduler")
            root.put("8", schedulerNode)

            // Node 9: RandomNoise
            val noiseNode = org.json.JSONObject()
            val noiseInputs = org.json.JSONObject()
            noiseInputs.put("noise_seed", finalSeed)
            noiseNode.put("inputs", noiseInputs)
            noiseNode.put("class_type", "RandomNoise")
            root.put("9", noiseNode)

            // Node 11: KSamplerSelect
            val samplerSelectNode = org.json.JSONObject()
            val samplerSelectInputs = org.json.JSONObject()
            samplerSelectInputs.put("sampler_name", "euler")
            samplerSelectNode.put("inputs", samplerSelectInputs)
            samplerSelectNode.put("class_type", "KSamplerSelect")
            root.put("11", samplerSelectNode)

            // Node 10: SamplerCustomAdvanced
            val samplerCustomNode = org.json.JSONObject()
            val samplerCustomInputs = org.json.JSONObject()
            samplerCustomInputs.put("noise", org.json.JSONArray().apply { put("9"); put(0) })
            samplerCustomInputs.put("guider", org.json.JSONArray().apply { put("7"); put(0) })
            samplerCustomInputs.put("sampler", org.json.JSONArray().apply { put("11"); put(0) })
            samplerCustomInputs.put("sigmas", org.json.JSONArray().apply { put("8"); put(0) })
            samplerCustomInputs.put("latent_image", org.json.JSONArray().apply { put("6"); put(0) })
            samplerCustomNode.put("inputs", samplerCustomInputs)
            samplerCustomNode.put("class_type", "SamplerCustomAdvanced")
            root.put("10", samplerCustomNode)

            // Node 12: VAEDecode
            val decodeNode = org.json.JSONObject()
            val decodeInputs = org.json.JSONObject()
            decodeInputs.put("samples", org.json.JSONArray().apply { put("10"); put(0) })
            decodeInputs.put("vae", org.json.JSONArray().apply { put("3"); put(0) })
            decodeNode.put("inputs", decodeInputs)
            decodeNode.put("class_type", "VAEDecode")
            root.put("12", decodeNode)

            // Node 13: SaveImage
            val saveNode = org.json.JSONObject()
            val saveInputs = org.json.JSONObject()
            saveInputs.put("images", org.json.JSONArray().apply { put("12"); put(0) })
            saveInputs.put("filename_prefix", savePrefix)
            saveNode.put("inputs", saveInputs)
            saveNode.put("class_type", "SaveImage")
            root.put("13", saveNode)

        } else {
            // STANDARD / SDXL / TURBO PIPELINE
            // 1. Checkpoint Loader Simple
            val ckptNode = org.json.JSONObject()
            val ckptInputs = org.json.JSONObject()
            ckptInputs.put("ckpt_name", ckptName)
            ckptNode.put("inputs", ckptInputs)
            ckptNode.put("class_type", "CheckpointLoaderSimple")
            root.put("1", ckptNode)

            // 2. VAE Loader (if not "Automatic")
            val useVae = _selectedVae.value != "Automatic"
            if (useVae) {
                val vaeNode = org.json.JSONObject()
                val vaeInputs = org.json.JSONObject()
                vaeInputs.put("vae_name", _selectedVae.value)
                vaeNode.put("inputs", vaeInputs)
                vaeNode.put("class_type", "VAELoader")
                root.put("10", vaeNode)
            }

            // 3. Chain LoRAs if selected
            var currentModelOutput = org.json.JSONArray().apply { put("1"); put(0) }
            var currentClipOutput = org.json.JSONArray().apply { put("1"); put(1) }

            val loraList = _selectedLoras.value
            loraList.forEachIndexed { index, lora ->
                val loraId = (50 + index).toString()
                val loraNode = org.json.JSONObject()
                val loraInputs = org.json.JSONObject()
                loraInputs.put("lora_name", lora.name)
                loraInputs.put("strength_model", lora.strength.toDouble())
                loraInputs.put("strength_clip", lora.strength.toDouble())
                loraInputs.put("model", currentModelOutput)
                loraInputs.put("clip", currentClipOutput)
                loraNode.put("inputs", loraInputs)
                loraNode.put("class_type", "LoraLoader")
                root.put(loraId, loraNode)

                currentModelOutput = org.json.JSONArray().apply { put(loraId); put(0) }
                currentClipOutput = org.json.JSONArray().apply { put(loraId); put(1) }
            }

            // CLIP Set Last Layer (Clip Skip)
            val cSkip = _clipSkip.value
            if (cSkip > 1) {
                val clipSkipId = "15"
                val clipSkipNode = org.json.JSONObject()
                val clipSkipInputs = org.json.JSONObject()
                clipSkipInputs.put("stop_at_clip_layer", -cSkip)
                clipSkipInputs.put("clip", currentClipOutput)
                clipSkipNode.put("inputs", clipSkipInputs)
                clipSkipNode.put("class_type", "CLIPSetLastLayer")
                root.put(clipSkipId, clipSkipNode)
                
                currentClipOutput = org.json.JSONArray().apply { put(clipSkipId); put(0) }
            }

            // 4. Positive Prompt text encode
            val posNode = org.json.JSONObject()
            val posInputs = org.json.JSONObject()
            posInputs.put("text", _positivePrompt.value)
            posInputs.put("clip", currentClipOutput)
            posNode.put("inputs", posInputs)
            posNode.put("class_type", "CLIPTextEncode")
            root.put("2", posNode)

            // 5. Negative Prompt text encode
            val negNode = org.json.JSONObject()
            val negInputs = org.json.JSONObject()
            negInputs.put("text", _negativePrompt.value)
            negInputs.put("clip", currentClipOutput)
            negNode.put("inputs", negInputs)
            negNode.put("class_type", "CLIPTextEncode")
            root.put("3", negNode)

            // 6. Empty Latent Image
            val latentNode = org.json.JSONObject()
            val latentInputs = org.json.JSONObject()
            latentInputs.put("width", _width.value)
            latentInputs.put("height", _height.value)
            latentInputs.put("batch_size", _batchCount.value)
            latentNode.put("inputs", latentInputs)
            latentNode.put("class_type", "EmptyLatentImage")
            root.put("4", latentNode)

            // 7. KSampler (base)
            val samplerNode = org.json.JSONObject()
            val samplerInputs = org.json.JSONObject()
            samplerInputs.put("seed", finalSeed)
            samplerInputs.put("steps", _steps.value)
            samplerInputs.put("cfg", _cfg.value.toDouble())
            samplerInputs.put("sampler_name", _sampler.value)
            samplerInputs.put("scheduler", _selectedScheduler.value)
            samplerInputs.put("denoise", 1.0)
            samplerInputs.put("model", currentModelOutput)
            samplerInputs.put("positive", org.json.JSONArray().apply { put("2"); put(0) })
            samplerInputs.put("negative", org.json.JSONArray().apply { put("3"); put(0) })
            samplerInputs.put("latent_image", org.json.JSONArray().apply { put("4"); put(0) })
            samplerNode.put("inputs", samplerInputs)
            samplerNode.put("class_type", "KSampler")
            root.put("5", samplerNode)

            val vaeRef = if (useVae) {
                org.json.JSONArray().apply { put("10"); put(0) }
            } else {
                org.json.JSONArray().apply { put("1"); put(2) }
            }

            if (!_hiresEnabled.value) {
                // Decoded base output
                val decodeNode = org.json.JSONObject()
                val decodeInputs = org.json.JSONObject()
                decodeInputs.put("samples", org.json.JSONArray().apply { put("5"); put(0) })
                decodeInputs.put("vae", vaeRef)
                decodeNode.put("inputs", decodeInputs)
                decodeNode.put("class_type", "VAEDecode")
                root.put("6", decodeNode)

                // SaveImage
                val saveNode = org.json.JSONObject()
                val saveInputs = org.json.JSONObject()
                saveInputs.put("filename_prefix", savePrefix)
                saveInputs.put("images", org.json.JSONArray().apply { put("6"); put(0) })
                saveNode.put("inputs", saveInputs)
                saveNode.put("class_type", "SaveImage")
                root.put("7", saveNode)
            } else {
                // Latent Upscale
                val upscaleNode = org.json.JSONObject()
                val upscaleInputs = org.json.JSONObject()
                upscaleInputs.put("samples", org.json.JSONArray().apply { put("5"); put(0) })
                upscaleInputs.put("upscale_method", "nearest-exact")
                
                // Rule 3: LatentUpscale width and height calculation snapped to nearest 64 multiple
                val factor = _hiresScale.value
                val originalWidth = _width.value
                val originalHeight = _height.value
                val scaledWidth = Math.round(originalWidth * factor / 64.0) * 64
                val scaledHeight = Math.round(originalHeight * factor / 64.0) * 64
                upscaleInputs.put("width", if (scaledWidth < 64) 64 else scaledWidth)
                upscaleInputs.put("height", if (scaledHeight < 64) 64 else scaledHeight)
                upscaleInputs.put("crop", "disabled")
                upscaleNode.put("inputs", upscaleInputs)
                upscaleNode.put("class_type", "LatentUpscale")
                root.put("6", upscaleNode)

                // KSampler (hires)
                val samplerHiresNode = org.json.JSONObject()
                val samplerHiresInputs = org.json.JSONObject()
                samplerHiresInputs.put("seed", finalSeed)
                samplerHiresInputs.put("steps", _hiresSteps.value)
                samplerHiresInputs.put("cfg", _cfg.value.toDouble())
                samplerHiresInputs.put("sampler_name", _sampler.value)
                samplerHiresInputs.put("scheduler", _selectedScheduler.value)
                // Rule 2: Limit denoise between 0.1 and 0.75
                val denoiseValue = _hiresDenoise.value.coerceIn(0.1f, 0.75f)
                samplerHiresInputs.put("denoise", denoiseValue.toDouble())
                samplerHiresInputs.put("model", currentModelOutput)
                samplerHiresInputs.put("positive", org.json.JSONArray().apply { put("2"); put(0) })
                samplerHiresInputs.put("negative", org.json.JSONArray().apply { put("3"); put(0) })
                samplerHiresInputs.put("latent_image", org.json.JSONArray().apply { put("6"); put(0) })
                samplerHiresNode.put("inputs", samplerHiresInputs)
                samplerHiresNode.put("class_type", "KSampler")
                root.put("7", samplerHiresNode)

                // VAEDecode (hires)
                val decodeHiresNode = org.json.JSONObject()
                val decodeHiresInputs = org.json.JSONObject()
                decodeHiresInputs.put("samples", org.json.JSONArray().apply { put("7"); put(0) })
                decodeHiresInputs.put("vae", vaeRef)
                decodeHiresNode.put("inputs", decodeHiresInputs)
                decodeHiresNode.put("class_type", "VAEDecode")
                root.put("8", decodeHiresNode)

                // SaveImage (hires)
                val saveHiresNode = org.json.JSONObject()
                val saveHiresInputs = org.json.JSONObject()
                saveHiresInputs.put("filename_prefix", savePrefix)
                saveHiresInputs.put("images", org.json.JSONArray().apply { put("8"); put(0) })
                saveHiresNode.put("inputs", saveHiresInputs)
                saveHiresNode.put("class_type", "SaveImage")
                root.put("9", saveHiresNode)
            }
        }

        // Intercept workflow to append ReActor nodes when Face Swap toggled ON and ReActor engine is designated.
        val reactorSelected = settingsManager.faceSwapEngine == "reactor"
        if (faceSwapActive && reactorSelected) {
            val sourceFilename = _genFaceSwapSourceFilename.value
            if (sourceFilename != null) {
                val decodeNodeId = if (mType == ModelType.FLUX) "12" else if (_hiresEnabled.value) "8" else "6"
                val saveNodeId = if (mType == ModelType.FLUX) "13" else if (_hiresEnabled.value) "9" else "7"

                // Node 199: LoadImage Node for sourceFace
                val loadImageNode = org.json.JSONObject()
                val loadImageInputs = org.json.JSONObject()
                loadImageInputs.put("image", sourceFilename)
                loadImageNode.put("inputs", loadImageInputs)
                loadImageNode.put("class_type", "LoadImage")
                root.put("199", loadImageNode)

                // Node 200: ReActorLoadFaceModel
                val loadFaceNode = org.json.JSONObject()
                val loadFaceInputs = org.json.JSONObject()
                loadFaceInputs.put("face_model", _reactorSelectedSwapModel.value)
                loadFaceNode.put("inputs", loadFaceInputs)
                loadFaceNode.put("class_type", "ReActorLoadFaceModel")
                root.put("200", loadFaceNode)

                // Node 201: ReActorFaceSwap
                val swapNode = org.json.JSONObject()
                val swapInputs = org.json.JSONObject()
                swapInputs.put("enabled", true)
                swapInputs.put("input_image", org.json.JSONArray().apply { put(decodeNodeId); put(0) })
                swapInputs.put("source_image", org.json.JSONArray().apply { put("199"); put(0) })
                swapInputs.put("swap_model", _reactorSelectedSwapModel.value)
                swapInputs.put("facedetection", _reactorSelectedFaceDetection.value)
                swapInputs.put("face_restore_model", _reactorSelectedRestoreModel.value)
                swapInputs.put("face_restore_visibility", _reactorRestoreVisibility.value.toDouble())
                swapInputs.put("codeformer_weight", _reactorCodeformerWeight.value.toDouble())
                swapInputs.put("detect_gender_source", _reactorSelectedGenderSource.value)
                swapInputs.put("detect_gender_input", _reactorSelectedGenderInput.value)
                swapInputs.put("source_faces_index", _reactorSourceFacesIndex.value)
                swapInputs.put("input_faces_index", _reactorInputFacesIndex.value)
                swapInputs.put("console_log_level", (_reactorNodeInfo.value?.consoleLogLevelDefault ?: "1").toIntOrNull() ?: 1)
                swapNode.put("inputs", swapInputs)
                swapNode.put("class_type", "ReActorFaceSwap")
                root.put("201", swapNode)

                // Reroute standard SaveImage input to Node 201
                val origSaveNode = root.optJSONObject(saveNodeId)
                if (origSaveNode != null) {
                    val saveInputsObj = origSaveNode.optJSONObject("inputs")
                    if (saveInputsObj != null) {
                        saveInputsObj.put("images", org.json.JSONArray().apply { put("201"); put(0) })
                    }
                }
            }
        }

        return sanitizeWorkflowTypes(root).toString()
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
            val faceSwapActive = _genFaceSwapEnabled.value
            val reactorSelected = settingsManager.faceSwapEngine == "reactor"
            if (faceSwapActive && reactorSelected) {
                val sourceFaceUri = _genFaceSwapSourceFaceUri.value
                if (sourceFaceUri == null) {
                    repository.setGenerationError("Source face photo is required for Face Swap.")
                    return@launch
                }
                var filename = _genFaceSwapSourceFilename.value
                if (filename == null) {
                    filename = repository.uploadImageToComfyUI(sourceFaceUri)
                    if (filename == null) {
                        repository.setGenerationError("Failed to upload face image to server. Check network connection.")
                        return@launch
                    }
                    _genFaceSwapSourceFilename.value = filename
                }
                try {
                    validateReActorAndPrepare()
                } catch (e: Exception) {
                    repository.setGenerationError(e.message ?: "ReActor validation failed.")
                    return@launch
                }
            }

            val finalSeed = if (_isSeedRandom.value) {
                (0..Long.MAX_VALUE).random()
            } else {
                if (_seed.value < 0) 123456L else _seed.value
            }

            val finalJson = if (_loadedWorkflow.value != null) {
                val loaded = _loadedWorkflow.value!!
                WorkflowParser.rebuildWorkflowJson(loaded.originalJson, loaded.orderedSections)
            } else if (_activeWorkflowId.value == null) {
                buildDynamicWorkflowJson(finalSeed)
            } else {
                val templateJson = repository.allPresets.first().find { it.id == _activeWorkflowId.value }?.jsonContent ?: defaultWorkflowTemplate
                repository.modifyWorkflowJson(
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
            }

            val sanitizedJson = try {
                sanitizeWorkflowTypes(org.json.JSONObject(finalJson)).toString()
            } catch (e: Exception) {
                finalJson
            }

            comfyClient.queuePrompt(sanitizedJson)
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
        loadLocalWorkflow(preset)
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

    fun fetchServerWorkflows() {
        viewModelScope.launch {
            _isFetchingServerWorkflows.value = true
            _serverSyncError.value = null
            try {
                val list = comfyClient.getServerWorkflows()
                _serverWorkflows.value = list
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching server workflows", e)
                _serverSyncError.value = "Server workflow sync requires ComfyUI 0.2.0+"
                _serverWorkflows.value = emptyList()
            } finally {
                _isFetchingServerWorkflows.value = false
            }
        }
    }

    fun loadServerWorkflow(name: String) {
        viewModelScope.launch {
            try {
                _isFetchingServerWorkflows.value = true
                val json = comfyClient.getServerWorkflowJson(name)
                if (json != null) {
                    val parsed = WorkflowParser.parseWorkflowJson(
                        jsonStr = json,
                        name = name,
                        source = WorkflowSource.SERVER,
                        objectInfo = cachedObjectInfo
                    )
                    _loadedWorkflow.value = parsed
                    _activeWorkflowId.value = null
                } else {
                    // Try history-based JSON if the direct fetch returned null
                    val historyMap = comfyClient.getWorkflowsFromHistory()
                    val histJson = historyMap[name]
                    if (histJson != null) {
                        val parsed = WorkflowParser.parseWorkflowJson(
                            jsonStr = histJson,
                            name = name,
                            source = WorkflowSource.SERVER,
                            objectInfo = cachedObjectInfo
                        )
                        _loadedWorkflow.value = parsed
                        _activeWorkflowId.value = null
                    } else {
                        Log.e(TAG, "Server returned null JSON for workflow: $name")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading server workflow: $name", e)
            } finally {
                _isFetchingServerWorkflows.value = false
            }
        }
    }

    fun loadLocalWorkflow(preset: WorkflowPreset) {
        viewModelScope.launch {
            try {
                val parsed = WorkflowParser.parseWorkflowJson(
                    jsonStr = preset.jsonContent,
                    name = preset.name,
                    source = WorkflowSource.LOCAL,
                    objectInfo = cachedObjectInfo
                )
                _loadedWorkflow.value = parsed
                _activeWorkflowId.value = preset.id
            } catch (e: Exception) {
                Log.e(TAG, "Error loading local workflow: ${preset.name}", e)
            }
        }
    }

    fun updateWorkflowField(nodeId: String, fieldName: String, newValue: Any) {
        val current = _loadedWorkflow.value ?: return
        val updatedSections = current.orderedSections.map { section ->
            if (section.nodeId == nodeId) {
                val updatedFields = section.editableFields.map { field ->
                    if (field.fieldName == fieldName) {
                        field.copy(currentValue = newValue)
                    } else {
                        field
                    }
                }
                section.copy(editableFields = updatedFields)
            } else {
                section
            }
        }
        _loadedWorkflow.value = current.copy(orderedSections = updatedSections)
    }

    fun uploadWorkflowImage(nodeId: String, fieldName: String, uri: Uri) {
        viewModelScope.launch {
            try {
                val filename = repository.uploadImageToComfyUI(uri)
                if (filename != null) {
                    updateWorkflowField(nodeId, fieldName, filename)
                } else {
                    Log.e(TAG, "Failed to upload workflow image")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading workflow image", e)
            }
        }
    }

    fun clearLoadedWorkflow() {
        _loadedWorkflow.value = null
        _activeWorkflowId.value = null
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

data class ReActorNodeInfo(
    val isAvailable: Boolean = false,
    val swapModels: List<String> = emptyList(),
    val faceDetections: List<String> = emptyList(),
    val faceRestoreModels: List<String> = emptyList(),
    val detectGenderSources: List<String> = emptyList(),
    val detectGenderInputs: List<String> = emptyList(),
    val faceRestoreVisibilityMin: Float = 0f,
    val faceRestoreVisibilityMax: Float = 1f,
    val faceRestoreVisibilityDefault: Float = 1f,
    val codeformerWeightMin: Float = 0f,
    val codeformerWeightMax: Float = 1f,
    val codeformerWeightDefault: Float = 0.5f,
    val consoleLogLevelDefault: String = "1",
    val swapModelDefault: String = "",
    val facedetectionDefault: String = "",
    val faceRestoreModelDefault: String = "",
    val detectGenderSourceDefault: String = "",
    val detectGenderInputDefault: String = ""
)
