package com.example.data.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.data.settings.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

enum class GenerationStatus {
    IDLE, QUEUED, EXECUTING, SUCCESS, ERROR
}

class ComfyClient(
    private val settingsManager: SettingsManager,
    private val externalScope: CoroutineScope
) {
    private val TAG = "ComfyClient"
    val clientId = UUID.randomUUID().toString()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for websockets / stream replies
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    // Live States
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _generationStatus = MutableStateFlow(GenerationStatus.IDLE)
    val generationStatus = _generationStatus.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep = _currentStep.asStateFlow()

    private val _totalSteps = MutableStateFlow(0)
    val totalSteps = _totalSteps.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap = _previewBitmap.asStateFlow()

    private val _promptId = MutableStateFlow<String?>(null)
    val promptId = _promptId.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _queueRemaining = MutableStateFlow(0)
    val queueRemaining = _queueRemaining.asStateFlow()

    fun getBaseUrl(): String {
        return "http://${settingsManager.serverIp}:${settingsManager.serverPort}"
    }

    fun getWsUrl(): String {
        return "ws://${settingsManager.serverIp}:${settingsManager.serverPort}/ws?clientId=$clientId"
    }

    fun connectWebSocket() {
        disconnectWebSocket()
        _connectionStatus.value = ConnectionStatus.CONNECTING

        val request = Request.Builder()
            .url(getWsUrl())
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket Opened")
                _connectionStatus.value = ConnectionStatus.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Text Message: $text")
                parseWebSocketMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Parse binary frame (preview images)
                decodePreviewFrame(bytes)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                Log.d(TAG, "WebSocket Closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                Log.d(TAG, "WebSocket Closed")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Failure: ${t.message}", t)
                _connectionStatus.value = ConnectionStatus.ERROR
            }
        })
    }

    fun disconnectWebSocket() {
        webSocket?.close(1000, "App request")
        webSocket = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    private fun parseWebSocketMessage(text: String) {
        try {
            val root = JSONObject(text)
            val type = root.optString("type")
            val data = root.optJSONObject("data") ?: return

            when (type) {
                "status" -> {
                    val statusObj = data.optJSONObject("status")
                    val execInfo = statusObj?.optJSONObject("exec_info")
                    _queueRemaining.value = execInfo?.optInt("queue_remaining", 0) ?: 0
                }
                "execution_start" -> {
                    val pId = data.optString("prompt_id")
                    _promptId.value = pId
                    _generationStatus.value = GenerationStatus.QUEUED
                    _isGenerating.value = true
                }
                "executing" -> {
                    val nodeId = data.optString("node")
                    val pId = data.optString("prompt_id")
                    if (nodeId.isEmpty() || nodeId == "null") {
                        // Empty/null nodeId means execution completed
                        if (_isGenerating.value && _promptId.value == pId) {
                            _generationStatus.value = GenerationStatus.SUCCESS
                            _isGenerating.value = false
                        }
                    } else {
                        _generationStatus.value = GenerationStatus.EXECUTING
                    }
                }
                "progress" -> {
                    val value = data.optInt("value", 0)
                    val max = data.optInt("max", 0)
                    _currentStep.value = value
                    _totalSteps.value = max
                    _generationStatus.value = GenerationStatus.EXECUTING
                }
                "execution_interrupted" -> {
                    _generationStatus.value = GenerationStatus.ERROR
                    _isGenerating.value = false
                    _promptId.value = null
                }
                "execution_error" -> {
                    _generationStatus.value = GenerationStatus.ERROR
                    _isGenerating.value = false
                    _promptId.value = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing websocket text message", e)
        }
    }

    private fun decodePreviewFrame(bytes: ByteString) {
        try {
            val bytesArray = bytes.toByteArray()
            if (bytesArray.size < 8) return

            // Search for SOI marker of JPEG (FF D8) or Standard PNG signature (89 50 4E 47)
            var imgStartIndex = -1

            // Check if JPEG
            for (i in 0 until bytesArray.size - 1) {
                if (bytesArray[i] == 0xFF.toByte() && bytesArray[i + 1] == 0xD8.toByte()) {
                    imgStartIndex = i
                    break
                }
            }

            // Check if PNG if JPEG not found
            if (imgStartIndex == -1) {
                for (i in 0 until bytesArray.size - 3) {
                    if (bytesArray[i] == 0x89.toByte() &&
                        bytesArray[i + 1] == 0x50.toByte() &&
                        bytesArray[i + 2] == 0x4E.toByte() &&
                        bytesArray[i + 3] == 0x47.toByte()
                    ) {
                        imgStartIndex = i
                        break
                    }
                }
            }

            if (imgStartIndex != -1) {
                val imgBytes = bytesArray.copyOfRange(imgStartIndex, bytesArray.size)
                val bitmap = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size)
                if (bitmap != null) {
                    _previewBitmap.value = bitmap
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding preview", e)
        }
    }

    suspend fun pingServer(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${getBaseUrl()}/queue")
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${getBaseUrl()}/queue")
            .get()
            .build()

        val startTime = System.currentTimeMillis()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    "Success: Server responded in ${duration}ms"
                } else {
                    "Failed: Server returned error ${response.code}"
                }
            }
        } catch (e: Exception) {
            "Failed: ${e.message}"
        }
    }

    // Queue generation job
    suspend fun queuePrompt(promptJson: String): String? = withContext(Dispatchers.IO) {
        _previewBitmap.value = null
        _currentStep.value = 0
        _totalSteps.value = 0
        _generationStatus.value = GenerationStatus.QUEUED
        _isGenerating.value = true

        val promptObj = JSONObject()
        try {
            promptObj.put("client_id", clientId)
            promptObj.put("prompt", JSONObject(promptJson))
        } catch (e: Exception) {
            Log.e(TAG, "Malformed prompt JSON", e)
            _generationStatus.value = GenerationStatus.ERROR
            _isGenerating.value = false
            return@withContext null
        }

        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            promptObj.toString()
        )

        val request = Request.Builder()
            .url("${getBaseUrl()}/prompt")
            .post(requestBody)
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respBody = response.body?.string() ?: ""
                    val root = JSONObject(respBody)
                    val pId = root.optString("prompt_id")
                    _promptId.value = pId
                    pId
                } else {
                    _generationStatus.value = GenerationStatus.ERROR
                    _isGenerating.value = false
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error posting prompt", e)
            _generationStatus.value = GenerationStatus.ERROR
            _isGenerating.value = false
            null
        }
    }

    // Cancel dynamic generation
    suspend fun cancelGeneration(): Boolean = withContext(Dispatchers.IO) {
        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            "{}"
        )
        val request = Request.Builder()
            .url("${getBaseUrl()}/interrupt")
            .post(requestBody)
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    _isGenerating.value = false
                    _generationStatus.value = GenerationStatus.IDLE
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    // Fetch details of generation history (this contains the saved filenames!)
    suspend fun getPromptHistory(promptId: String): JSONObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${getBaseUrl()}/history/$promptId")
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    JSONObject(body)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // Get input stream for file
    fun getImageViewStream(filename: String, subfolder: String = "", type: String = "output"): InputStream? {
        val baseHttpUrl = getBaseUrl().toHttpUrlOrNull()!!.newBuilder()
            .addPathSegment("view")
            .addQueryParameter("filename", filename)
            .addQueryParameter("subfolder", subfolder)
            .addQueryParameter("type", type)
            .build()

        val request = Request.Builder()
            .url(baseHttpUrl)
            .get()
            .build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()
            } else {
                response.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // Fetch server available objects
    suspend fun getObjectInfo(): JSONObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${getBaseUrl()}/object_info")
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    JSONObject(response.body?.string() ?: "")
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // Fetch server available embeddings
    suspend fun getEmbeddings(): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${getBaseUrl()}/embeddings")
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "[]"
                    val array = org.json.JSONArray(body)
                    List(array.length()) { array.getString(it) }
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
