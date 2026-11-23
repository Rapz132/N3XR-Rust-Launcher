package com.movtery.zalithlauncher.music

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID

private const val TAG = "MusicManager"

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    /** Path file (lokal) atau URL stream (Audius) */
    val uri: String,
    val durationMs: Long,
    val isLocal: Boolean
)

/**
 * Pemutar musik: lagu lokal (disalin ke penyimpanan launcher) dan streaming Audius.
 * Semua fungsi yang menyentuh player harus dipanggil dari main thread.
 */
object MusicManager {
    private const val APP_NAME = "N3xLauncher"
    private const val AUDIUS_HOST = "api.audius.co"

    private val gson = Gson()
    private val client = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var player: ExoPlayer? = null
    private var appContext: Context? = null
    private var queue: List<MusicTrack> = emptyList()

    private val _library = MutableStateFlow<List<MusicTrack>>(emptyList())
    val library: StateFlow<List<MusicTrack>> = _library.asStateFlow()

    private val _results = MutableStateFlow<List<MusicTrack>>(emptyList())
    val results: StateFlow<List<MusicTrack>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _current = MutableStateFlow<MusicTrack?>(null)
    val current: StateFlow<MusicTrack?> = _current.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Panggil dari main thread. Aman dipanggil berulang. */
    fun init(context: Context) {
        if (player != null) return
        val app = context.applicationContext
        appContext = app
        val exo = ExoPlayer.Builder(app).build()
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        exo.repeatMode = Player.REPEAT_MODE_ALL
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId
                _current.value = queue.firstOrNull { it.id == id }
            }

            override fun onPlayerError(error: PlaybackException) {
                Logger.warning(TAG, "Playback error: ${error.message}")
                _error.value = "Gagal memutar: ${error.message ?: "kesalahan tidak diketahui"}"
            }
        })
        player = exo
        loadLibrary()
    }

    // ------------------------------------------------------------------
    //  Kontrol pemutaran
    // ------------------------------------------------------------------

    fun play(tracks: List<MusicTrack>, index: Int) {
        val p = player ?: return
        if (tracks.isEmpty() || index !in tracks.indices) return
        _error.value = null
        queue = tracks
        p.setMediaItems(tracks.map { toMediaItem(it) }, index, 0L)
        p.prepare()
        p.play()
        _current.value = tracks[index]
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            _error.value = null
            if (p.playbackState == Player.STATE_IDLE) p.prepare()
            p.play()
        }
    }

    fun next() {
        player?.seekToNextMediaItem()
    }

    fun previous() {
        val p = player ?: return
        if (p.currentPosition > 3000) p.seekTo(0) else p.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        player?.seekTo(ms)
    }

    fun toggleShuffle() {
        val p = player ?: return
        p.shuffleModeEnabled = !p.shuffleModeEnabled
        _shuffle.value = p.shuffleModeEnabled
    }

    fun position(): Long = player?.currentPosition ?: 0L

    fun duration(): Long {
        val d = player?.duration ?: C.TIME_UNSET
        return if (d == C.TIME_UNSET || d < 0) _current.value?.durationMs ?: 0L else d
    }

    private fun toMediaItem(track: MusicTrack): MediaItem {
        val uri = if (track.isLocal) Uri.fromFile(File(track.uri)) else Uri.parse(track.uri)
        return MediaItem.Builder().setUri(uri).setMediaId(track.id).build()
    }

    // ------------------------------------------------------------------
    //  Lagu lokal
    // ------------------------------------------------------------------

    private fun musicDir(context: Context): File =
        File(context.filesDir, "music").apply { mkdirs() }

    private fun libraryFile(context: Context): File = File(musicDir(context), "library.json")

    private fun loadLibrary() {
        val ctx = appContext ?: return
        scope.launch {
            val file = libraryFile(ctx)
            if (!file.exists()) return@launch
            runCatching {
                val type = object : TypeToken<List<MusicTrack>>() {}.type
                val list: List<MusicTrack> = gson.fromJson(file.readText(), type) ?: emptyList()
                _library.value = list.filter { File(it.uri).exists() }
            }.onFailure { Logger.warning(TAG, "Failed to load library: ${it.message}") }
        }
    }

    private fun saveLibrary() {
        val ctx = appContext ?: return
        runCatching {
            libraryFile(ctx).writeText(gson.toJson(_library.value))
        }.onFailure { Logger.warning(TAG, "Failed to save library: ${it.message}") }
    }

    /** Salin lagu pilihan ke penyimpanan launcher. Panggil dari thread IO. Mengembalikan jumlah yang berhasil. */
    fun addLocal(context: Context, uris: List<Uri>): Int {
        val dir = musicDir(context)
        val added = mutableListOf<MusicTrack>()
        for (uri in uris) {
            runCatching {
                val name = queryName(context, uri) ?: "lagu"
                val ext = name.substringAfterLast('.', "mp3").take(5)
                val id = UUID.randomUUID().toString()
                val file = File(dir, "$id.$ext")
                val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Tidak bisa membuka file")
                input.use { i -> file.outputStream().use { o -> i.copyTo(o) } }
                val (title, artist, duration) = readMeta(file, name.substringBeforeLast('.'))
                added += MusicTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    uri = file.absolutePath,
                    durationMs = duration,
                    isLocal = true
                )
            }.onFailure { Logger.warning(TAG, "Failed to import audio: ${it.message}") }
        }
        if (added.isNotEmpty()) {
            _library.update { it + added }
            saveLibrary()
        }
        return added.size
    }

    /** Panggil dari main thread. */
    fun removeLocal(track: MusicTrack) {
        if (_current.value?.id == track.id) {
            player?.stop()
            player?.clearMediaItems()
            queue = emptyList()
            _current.value = null
        }
        runCatching { File(track.uri).delete() }
        _library.update { list -> list.filterNot { it.id == track.id } }
        saveLibrary()
    }

    private fun queryName(context: Context, uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }

    private fun readMeta(file: File, fallbackTitle: String): Triple<String, String, Long> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Artis tidak diketahui"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            Triple(title, artist, duration)
        } catch (e: Exception) {
            Triple(fallbackTitle, "Artis tidak diketahui", 0L)
        } finally {
            runCatching { retriever.release() }
        }
    }

    // ------------------------------------------------------------------
    //  Audius
    // ------------------------------------------------------------------

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _searching.value = true
        _searchError.value = null
        scope.launch {
            try {
                val url = HttpUrl.Builder()
                    .scheme("https")
                    .host(AUDIUS_HOST)
                    .addPathSegments("v1/tracks/search")
                    .addQueryParameter("query", q)
                    .addQueryParameter("limit", "25")
                    .addQueryParameter("app_name", APP_NAME)
                    .build()
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body.string()
                    val data = JsonParser.parseString(body).asJsonObject.getAsJsonArray("data")
                    val list = data?.mapNotNull { parseAudiusTrack(it) } ?: emptyList()
                    _results.value = list
                    if (list.isEmpty()) _searchError.value = "Tidak ada hasil."
                }
            } catch (e: Exception) {
                Logger.warning(TAG, "Audius search failed: ${e.message}")
                _searchError.value = "Gagal mencari: ${e.message ?: "periksa koneksi internet"}"
            } finally {
                _searching.value = false
            }
        }
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun parseAudiusTrack(element: JsonElement): MusicTrack? = runCatching {
        val obj = element.asJsonObject
        val id = obj.str("id") ?: return null
        val title = obj.str("title") ?: return null
        val streamable = obj.get("is_streamable")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true
        if (!streamable) return null
        val artist = obj.getAsJsonObject("user")?.str("name") ?: "Artis tidak diketahui"
        val seconds = obj.get("duration")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
        MusicTrack(
            id = "audius-$id",
            title = title,
            artist = artist,
            uri = "https://$AUDIUS_HOST/v1/tracks/$id/stream?app_name=$APP_NAME",
            durationMs = seconds * 1000,
            isLocal = false
        )
    }.getOrNull()
}
