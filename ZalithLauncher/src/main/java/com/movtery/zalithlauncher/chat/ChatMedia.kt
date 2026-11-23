package com.movtery.zalithlauncher.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/** Hasil rekaman suara */
class VoiceRecording(val base64: String, val durationMs: Long)

object ChatMedia {
    private const val SEND_MAX_DIMENSION = 1024
    private const val VIEW_MAX_DIMENSION = 2048

    /** Kecilkan dan kompres foto jadi JPEG base64 yang muat di batas ukuran pesan. */
    fun encodeImage(context: Context, uri: Uri): String? {
        try {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > SEND_MAX_DIMENSION * 2) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            val rotation = resolver.openInputStream(uri)?.use { readRotation(it) } ?: 0
            var bitmap = scaleAndRotate(decoded, SEND_MAX_DIMENSION, rotation)

            var quality = 72
            repeat(12) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                if (base64.length <= ChatManager.MAX_MEDIA_CHARS) return base64

                if (quality > 40) {
                    quality -= 12
                } else {
                    val w = (bitmap.width * 0.8f).toInt()
                    val h = (bitmap.height * 0.8f).toInt()
                    if (w < 64 || h < 64) return null
                    bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    /** Decode foto base64 (dengan batas ukuran supaya aman dari gambar raksasa). */
    fun decodeImage(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > VIEW_MAX_DIMENSION) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: Throwable) {
            null
        }
    }

    private fun readRotation(stream: InputStream): Int {
        return try {
            when (
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun scaleAndRotate(src: Bitmap, maxDim: Int, rotation: Int): Bitmap {
        val scale = minOf(1f, maxDim.toFloat() / maxOf(src.width, src.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }
}

/** Perekam pesan suara (AAC, mono, ±3KB per detik). */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

    fun start(): Boolean {
        cancel()
        val target = File(context.cacheDir, "chat_rec_${System.currentTimeMillis()}.m4a")
        var r: MediaRecorder? = null
        return try {
            r = createRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(24000)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
            recorder = r
            file = target
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            runCatching { r?.release() }
            target.delete()
            false
        }
    }

    /** Hentikan rekaman. Null kalau terlalu pendek atau gagal. */
    fun stop(): VoiceRecording? {
        val r = recorder ?: return null
        val f = file
        val duration = System.currentTimeMillis() - startedAt
        recorder = null
        file = null

        val stopped = try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            false
        }
        runCatching { r.release() }

        if (f == null) return null
        val result = if (stopped && duration >= 500L) {
            try {
                VoiceRecording(Base64.encodeToString(f.readBytes(), Base64.NO_WRAP), duration)
            } catch (e: Exception) {
                null
            }
        } else null
        f.delete()
        return result?.takeIf { it.base64.length <= ChatManager.MAX_MEDIA_CHARS }
    }

    fun cancel() {
        val r = recorder
        val f = file
        recorder = null
        file = null
        if (r != null) {
            runCatching { r.stop() }
            runCatching { r.release() }
        }
        f?.delete()
    }
}

/** Pemutar pesan suara, hanya satu suara yang diputar sekali waktu. */
class VoicePlayer(
    private val context: Context,
    private val onPlayingChange: (String?) -> Unit
) {
    private var player: MediaPlayer? = null
    private var playingId: String? = null

    fun toggle(id: String, base64: String) {
        if (playingId == id) {
            stop()
            return
        }
        stop()

        // id berasal dari pengirim lain, bersihkan sebelum dipakai jadi nama file
        val safeId = id.filter { it.isLetterOrDigit() || it == '-' }.take(40).ifEmpty { "voice" }
        val mp = MediaPlayer()
        try {
            val f = File(context.cacheDir, "chat_play_$safeId.m4a")
            if (!f.exists()) f.writeBytes(Base64.decode(base64, Base64.NO_WRAP))
            mp.setDataSource(f.absolutePath)
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            mp.prepare()
            mp.start()
            player = mp
            playingId = id
            onPlayingChange(id)
        } catch (e: Exception) {
            runCatching { mp.release() }
            stop()
        }
    }

    fun stop() {
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        playingId = null
        onPlayingChange(null)
    }

    fun release() {
        stop()
        context.cacheDir.listFiles { f -> f.name.startsWith("chat_play_") }?.forEach { it.delete() }
    }
}
