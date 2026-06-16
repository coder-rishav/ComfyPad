package com.example.data.settings

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("comfypad_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_SERVER_IP = "server_ip"
        const val KEY_SERVER_PORT = "server_port"
        const val KEY_DEFAULT_STEPS = "default_steps"
        const val KEY_DEFAULT_CFG = "default_cfg"
        const val KEY_DEFAULT_WIDTH = "default_width"
        const val KEY_DEFAULT_HEIGHT = "default_height"
        const val KEY_THEME = "app_theme" // "dark", "light", "system"
        const val KEY_SAVE_TO_GALLERY = "save_to_gallery"
        
        const val KEY_SELECTED_CHECKPOINT = "selected_ckpt"
        const val KEY_SELECTED_VAE = "selected_vae"
        const val KEY_SELECTED_SAMPLER = "selected_sampler"
        const val KEY_SELECTED_SCHEDULER = "selected_scheduler"
        const val KEY_SELECTED_LORAS = "selected_loras" // store as JSON array
        const val KEY_CLIP_SKIP = "clip_skip"
        const val KEY_HIRES_ENABLED = "hires_enabled"
        const val KEY_HIRES_UPSCALER = "hires_upscaler"
        const val KEY_HIRES_SCALE = "hires_scale"
        const val KEY_HIRES_STEPS = "hires_steps"
        const val KEY_HIRES_DENOISE = "hires_denoise"
        const val KEY_BATCH_COUNT = "batch_count"
        
        const val KEY_FACE_SWAP_ENGINE = "face_swap_engine"
        const val KEY_FACE_FUSION_URL = "face_fusion_url"
        const val KEY_SELECTED_CLIP1 = "selected_clip1"
        const val KEY_SELECTED_CLIP2 = "selected_clip2"
        
        const val KEY_GEN_FACE_SWAP_ENABLED = "gen_face_swap_enabled"
        const val KEY_GEN_FACE_SWAP_SOURCE_URI = "gen_face_swap_source_uri"
        const val KEY_GEN_FACE_SWAP_RESTORE_ENABLED = "gen_face_swap_restore_enabled"
        const val KEY_GEN_FACE_SWAP_RESTORE_MODEL = "gen_face_swap_restore_model"
        const val KEY_GEN_FACE_SWAP_VISIBILITY = "gen_face_swap_visibility"
        const val KEY_GEN_FACE_SWAP_WEIGHT = "gen_face_swap_weight"
    }

    var serverIp: String
        get() = prefs.getString(KEY_SERVER_IP, "192.168.1.100") ?: "192.168.1.100"
        set(value) = prefs.edit().putString(KEY_SERVER_IP, value).apply()

    var serverPort: Int
        get() = prefs.getInt(KEY_SERVER_PORT, 8188)
        set(value) = prefs.edit().putInt(KEY_SERVER_PORT, value).apply()

    var defaultSteps: Int
        get() = prefs.getInt(KEY_DEFAULT_STEPS, 20)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_STEPS, value).apply()

    var defaultCfg: Float
        get() = prefs.getFloat(KEY_DEFAULT_CFG, 7.0f)
        set(value) = prefs.edit().putFloat(KEY_DEFAULT_CFG, value).apply()

    var defaultWidth: Int
        get() = prefs.getInt(KEY_DEFAULT_WIDTH, 512)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_WIDTH, value).apply()

    var defaultHeight: Int
        get() = prefs.getInt(KEY_DEFAULT_HEIGHT, 512)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_HEIGHT, value).apply()

    var appTheme: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var saveToGallery: Boolean
        get() = prefs.getBoolean(KEY_SAVE_TO_GALLERY, true)
        set(value) = prefs.edit().putBoolean(KEY_SAVE_TO_GALLERY, value).apply()

    var selectedCheckpoint: String?
        get() = prefs.getString(KEY_SELECTED_CHECKPOINT, null)
        set(value) = prefs.edit().putString(KEY_SELECTED_CHECKPOINT, value).apply()

    var selectedVae: String
        get() = prefs.getString(KEY_SELECTED_VAE, "Automatic") ?: "Automatic"
        set(value) = prefs.edit().putString(KEY_SELECTED_VAE, value).apply()

    var selectedSampler: String
        get() = prefs.getString(KEY_SELECTED_SAMPLER, "euler") ?: "euler"
        set(value) = prefs.edit().putString(KEY_SELECTED_SAMPLER, value).apply()

    var selectedScheduler: String
        get() = prefs.getString(KEY_SELECTED_SCHEDULER, "karras") ?: "karras"
        set(value) = prefs.edit().putString(KEY_SELECTED_SCHEDULER, value).apply()

    var selectedLorasJson: String
        get() = prefs.getString(KEY_SELECTED_LORAS, "[]") ?: "[]"
        set(value) = prefs.edit().putString(KEY_SELECTED_LORAS, value).apply()

    var clipSkip: Int
        get() = prefs.getInt(KEY_CLIP_SKIP, 1)
        set(value) = prefs.edit().putInt(KEY_CLIP_SKIP, value).apply()

    var hiresEnabled: Boolean
        get() = prefs.getBoolean(KEY_HIRES_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_HIRES_ENABLED, value).apply()

    var hiresUpscaler: String?
        get() = prefs.getString(KEY_HIRES_UPSCALER, null)
        set(value) = prefs.edit().putString(KEY_HIRES_UPSCALER, value).apply()

    var hiresScale: Float
        get() = prefs.getFloat(KEY_HIRES_SCALE, 1.5f)
        set(value) = prefs.edit().putFloat(KEY_HIRES_SCALE, value).apply()

    var hiresSteps: Int
        get() = prefs.getInt(KEY_HIRES_STEPS, 10)
        set(value) = prefs.edit().putInt(KEY_HIRES_STEPS, value).apply()

    var hiresDenoise: Float
        get() = prefs.getFloat(KEY_HIRES_DENOISE, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_HIRES_DENOISE, value).apply()

    var batchCount: Int
        get() = prefs.getInt(KEY_BATCH_COUNT, 1)
        set(value) = prefs.edit().putInt(KEY_BATCH_COUNT, value).apply()

    var assetsCacheJson: String
        get() = prefs.getString("assets_cache_json", "") ?: ""
        set(value) = prefs.edit().putString("assets_cache_json", value).apply()

    var faceSwapEngine: String
        get() = prefs.getString(KEY_FACE_SWAP_ENGINE, "reactor") ?: "reactor"
        set(value) = prefs.edit().putString(KEY_FACE_SWAP_ENGINE, value).apply()

    var faceFusionUrl: String
        get() = prefs.getString(KEY_FACE_FUSION_URL, "http://localhost:7860") ?: "http://localhost:7860"
        set(value) = prefs.edit().putString(KEY_FACE_FUSION_URL, value).apply()

    var selectedClip1: String
        get() = prefs.getString(KEY_SELECTED_CLIP1, "t5xxl_fp8_e4m3fn.safetensors") ?: "t5xxl_fp8_e4m3fn.safetensors"
        set(value) = prefs.edit().putString(KEY_SELECTED_CLIP1, value).apply()

    var selectedClip2: String
        get() = prefs.getString(KEY_SELECTED_CLIP2, "clip_l.safetensors") ?: "clip_l.safetensors"
        set(value) = prefs.edit().putString(KEY_SELECTED_CLIP2, value).apply()

    var genFaceSwapEnabled: Boolean
        get() = prefs.getBoolean(KEY_GEN_FACE_SWAP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GEN_FACE_SWAP_ENABLED, value).apply()

    var genFaceSwapSourceFaceUri: String
        get() = prefs.getString(KEY_GEN_FACE_SWAP_SOURCE_URI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEN_FACE_SWAP_SOURCE_URI, value).apply()

    var genFaceSwapRestoreEnabled: Boolean
        get() = prefs.getBoolean(KEY_GEN_FACE_SWAP_RESTORE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_GEN_FACE_SWAP_RESTORE_ENABLED, value).apply()

    var genFaceSwapRestoreModel: String
        get() = prefs.getString(KEY_GEN_FACE_SWAP_RESTORE_MODEL, "CodeFormer") ?: "CodeFormer"
        set(value) = prefs.edit().putString(KEY_GEN_FACE_SWAP_RESTORE_MODEL, value).apply()

    var genFaceSwapVisibility: Float
        get() = prefs.getFloat(KEY_GEN_FACE_SWAP_VISIBILITY, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_GEN_FACE_SWAP_VISIBILITY, value).apply()

    var genFaceSwapWeight: Float
        get() = prefs.getFloat(KEY_GEN_FACE_SWAP_WEIGHT, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_GEN_FACE_SWAP_WEIGHT, value).apply()
}
