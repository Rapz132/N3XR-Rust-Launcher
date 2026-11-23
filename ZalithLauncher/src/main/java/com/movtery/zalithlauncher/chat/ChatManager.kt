package com.movtery.zalithlauncher.chat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.movtery.zalithlauncher.BuildConfig
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "ChatManager"

enum class ChatMessageType { Text, Image, Audio }

data class ChatMessage(
    val id: String,
    val senderId: String,
    val sender: String,
    val text: String,
    val isAdmin: Boolean,
    val time: Long,
    val isSystem: Boolean = false,
    val type: ChatMessageType = ChatMessageType.Text,
    /** Data foto / suara dalam base64 (null untuk pesan teks) */
    val media: String? = null,
    /** Durasi pesan suara (ms) */
    val durationMs: Long = 0L
)

enum class ChatState { Idle, Connecting, Connected, Error }

/**
 * Chat grup singkat lewat Supabase Realtime Broadcast.
 * Pesan hanya ada di memori, tidak disimpan di database.
 */
object ChatManager {
    const val COOLDOWN_MS = 3000L
    const val CODE_LENGTH = 8

    /** Batas panjang base64 untuk foto / suara (Supabase free membatasi pesan ±250KB) */
    const val MAX_MEDIA_CHARS = 180_000

    /** Durasi maksimal pesan suara */
    const val MAX_AUDIO_MS = 30_000L

    private const val MAX_TEXT = 300
    private const val MAX_MESSAGES = 100
    private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** ID unik tiap kali aplikasi dibuka, dipakai menandai pesan milik sendiri */
    val clientId: String = UUID.randomUUID().toString()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _state = MutableStateFlow(ChatState.Idle)
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lastSentAt = MutableStateFlow(0L)
    val lastSentAt: StateFlow<Long> = _lastSentAt.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val refCounter = AtomicInteger(0)
    private var socket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var topic = ""
    private var joinRef = ""
    private var userName = "Pemain"

    /** Buat grup baru, pembuatnya jadi Admin. Mengembalikan kode grup. */
    fun createRoom(name: String): String {
        val random = SecureRandom()
        val code = buildString {
            repeat(CODE_LENGTH) { append(CODE_CHARS[random.nextInt(CODE_CHARS.length)]) }
        }
        start(code, name, admin = true)
        return code
    }

    fun joinRoom(code: String, name: String) {
        start(code.trim().uppercase(), name, admin = false)
    }

    fun leave() {
        if (_state.value == ChatState.Connected) {
            broadcast("leave", JsonObject().apply { addProperty("name", userName) })
        }
        stop()
        _roomCode.value = null
        _isAdmin.value = false
        _messages.value = emptyList()
        _error.value = null
        _state.value = ChatState.Idle
    }

    /** Kirim pesan. False kalau ditolak (kosong, belum tersambung, atau masih cooldown). */
    fun sendMessage(text: String): Boolean {
        val clean = text.trim().take(MAX_TEXT)
        if (clean.isEmpty()) return false
        return sendPayload {
            addProperty("type", "text")
            addProperty("text", clean)
        }
    }

    /** Kirim foto (JPEG base64). */
    fun sendImage(base64: String): Boolean {
        if (base64.isEmpty() || base64.length > MAX_MEDIA_CHARS) return false
        return sendPayload {
            addProperty("type", "image")
            addProperty("media", base64)
        }
    }

    /** Kirim pesan suara (AAC/M4A base64). */
    fun sendAudio(base64: String, durationMs: Long): Boolean {
        if (base64.isEmpty() || base64.length > MAX_MEDIA_CHARS) return false
        return sendPayload {
            addProperty("type", "audio")
            addProperty("media", base64)
            addProperty("dur", durationMs.coerceIn(0L, MAX_AUDIO_MS))
        }
    }

    private fun sendPayload(fill: JsonObject.() -> Unit): Boolean {
        if (_state.value != ChatState.Connected) return false
        val now = System.currentTimeMillis()
        if (now - _lastSentAt.value < COOLDOWN_MS) return false
        _lastSentAt.value = now
        broadcast("msg", JsonObject().apply {
            addProperty("id", UUID.randomUUID().toString())
            addProperty("senderId", clientId)
            addProperty("name", userName)
            addProperty("admin", _isAdmin.value)
            addProperty("ts", now)
            fill()
        })
        return true
    }

    @Synchronized
    private fun start(code: String, name: String, admin: Boolean) {
        stop()
        val url = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val key = BuildConfig.SUPABASE_KEY.trim()
        if (url.isEmpty() || key.isEmpty()) {
            _error.value = "Supabase belum dikonfigurasi di build."
            _state.value = ChatState.Error
            return
        }
        userName = name.ifBlank { "Pemain" }
        topic = "realtime:n3x-chat-$code"
        _messages.value = emptyList()
        _roomCode.value = code
        _isAdmin.value = admin
        _error.value = null
        _state.value = ChatState.Connecting

        val wsUrl = url
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") +
            "/realtime/v1/websocket?apikey=$key&vsn=1.0.0"
        socket = client.newWebSocket(Request.Builder().url(wsUrl).build(), listener)
    }

    @Synchronized
    private fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        val ws = socket
        socket = null
        ws?.close(1000, "bye")
    }

    private fun nextRef(): String = refCounter.incrementAndGet().toString()

    private fun broadcast(event: String, data: JsonObject) {
        val payload = JsonObject().apply {
            addProperty("type", "broadcast")
            addProperty("event", event)
            add("payload", data)
        }
        val frame = JsonObject().apply {
            addProperty("topic", topic)
            addProperty("event", "broadcast")
            add("payload", payload)
            addProperty("ref", nextRef())
        }
        socket?.send(frame.toString())
    }

    private fun sendJoin(ws: WebSocket) {
        joinRef = nextRef()
        val config = JsonObject().apply {
            add("broadcast", JsonObject().apply {
                addProperty("self", true)
                addProperty("ack", false)
            })
            add("presence", JsonObject().apply { addProperty("key", "") })
            addProperty("private", false)
        }
        val frame = JsonObject().apply {
            addProperty("topic", topic)
            addProperty("event", "phx_join")
            add("payload", JsonObject().apply { add("config", config) })
            addProperty("ref", joinRef)
        }
        ws.send(frame.toString())
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(25_000)
                val frame = JsonObject().apply {
                    addProperty("topic", "phoenix")
                    addProperty("event", "heartbeat")
                    add("payload", JsonObject())
                    addProperty("ref", nextRef())
                }
                ws.send(frame.toString())
            }
        }
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun addMessage(message: ChatMessage) {
        _messages.update { (it + message).takeLast(MAX_MESSAGES) }
    }

    private fun parseMessage(data: JsonObject): ChatMessage? {
        val type = when (data.str("type")) {
            "image" -> ChatMessageType.Image
            "audio" -> ChatMessageType.Audio
            else -> ChatMessageType.Text
        }
        val media = data.str("media")
        if (type != ChatMessageType.Text && (media == null || media.length > MAX_MEDIA_CHARS + 1_000)) {
            return null
        }
        val duration = data.get("dur")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L

        return ChatMessage(
            id = data.str("id") ?: UUID.randomUUID().toString(),
            senderId = data.str("senderId") ?: "",
            sender = data.str("name") ?: "Pemain",
            text = (data.str("text") ?: "").take(MAX_TEXT),
            isAdmin = data.get("admin")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            time = data.get("ts")?.takeIf { it.isJsonPrimitive }?.asLong
                ?: System.currentTimeMillis(),
            type = type,
            media = if (type == ChatMessageType.Text) null else media,
            durationMs = duration.coerceIn(0L, MAX_AUDIO_MS + 2_000L)
        )
    }

    private fun handle(text: String) {
        val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return
        val event = obj.str("event")
        val msgTopic = obj.str("topic")
        when (event) {
            "phx_reply" -> {
                if (obj.str("ref") != joinRef) return
                val status = obj.obj("payload")?.str("status")
                if (status == "ok") {
                    _state.value = ChatState.Connected
                    broadcast("join", JsonObject().apply { addProperty("name", userName) })
                } else {
                    _error.value = "Gagal masuk grup."
                    _state.value = ChatState.Error
                }
            }
            "broadcast" -> {
                if (msgTopic != topic) return
                val payload = obj.obj("payload") ?: return
                val data = payload.obj("payload") ?: return
                when (payload.str("event")) {
                    "msg" -> parseMessage(data)?.let { addMessage(it) }
                    "join" -> addSystem("${data.str("name") ?: "Pemain"} bergabung")
                    "leave" -> addSystem("${data.str("name") ?: "Pemain"} keluar")
                }
            }
            "phx_error", "phx_close" -> {
                if (msgTopic == topic) {
                    _error.value = "Koneksi terputus."
                    _state.value = ChatState.Error
                }
            }
        }
    }

    private fun addSystem(text: String) {
        addMessage(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                senderId = "",
                sender = "",
                text = text,
                isAdmin = false,
                time = System.currentTimeMillis(),
                isSystem = true
            )
        )
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (webSocket !== socket) return
            sendJoin(webSocket)
            startHeartbeat(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket !== socket) return
            runCatching { handle(text) }
                .onFailure { Logger.warning(TAG, "Failed to handle message: ${it.message}") }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket !== socket) return
            heartbeatJob?.cancel()
            _error.value = "Koneksi ditutup."
            _state.value = ChatState.Error
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== socket) return
            heartbeatJob?.cancel()
            Logger.warning(TAG, "WebSocket failure: ${t.message}")
            _error.value = t.message ?: "Koneksi gagal."
            _state.value = ChatState.Error
        }
    }
}
