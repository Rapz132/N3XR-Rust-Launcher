package com.movtery.zalithlauncher.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.movtery.zalithlauncher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Layanan latar belakang: menjaga musik tetap hidup saat game berjalan
 * dan menampilkan notifikasi dengan kontrol (sebelumnya, play/jeda, berikutnya, tutup).
 */
class MusicService : Service() {
    companion object {
        const val ACTION_TOGGLE = "com.movtery.zalithlauncher.music.TOGGLE"
        const val ACTION_NEXT = "com.movtery.zalithlauncher.music.NEXT"
        const val ACTION_PREV = "com.movtery.zalithlauncher.music.PREV"
        const val ACTION_CLOSE = "com.movtery.zalithlauncher.music.CLOSE"
        private const val CHANNEL_ID = "n3x_music"
        private const val NOTIFICATION_ID = 7301
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Pemutar musik", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )

        session = MediaSession(this, "N3xMusic").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (!MusicManager.isPlaying.value) MusicManager.toggle()
                }

                override fun onPause() {
                    if (MusicManager.isPlaying.value) MusicManager.toggle()
                }

                override fun onSkipToNext() = MusicManager.next()
                override fun onSkipToPrevious() = MusicManager.previous()
            })
            isActive = true
        }

        scope.launch {
            combine(MusicManager.current, MusicManager.isPlaying) { track, playing -> track to playing }
                .collect { (track, playing) ->
                    if (track == null) {
                        shutdown()
                    } else {
                        show(track, playing)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> MusicManager.toggle()
            ACTION_NEXT -> MusicManager.next()
            ACTION_PREV -> MusicManager.previous()
            ACTION_CLOSE -> {
                MusicManager.stop()
                shutdown()
                return START_NOT_STICKY
            }
        }
        // Wajib memanggil startForeground secepatnya setelah dimulai lewat startForegroundService
        show(MusicManager.current.value, MusicManager.isPlaying.value)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun shutdown() {
        foreground = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun action(iconRes: Int, label: String, action: String, code: Int): Notification.Action {
        val intent = Intent(this, MusicService::class.java).setAction(action)
        val pending = PendingIntent.getService(
            this, code, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Action.Builder(Icon.createWithResource(this, iconRes), label, pending).build()
    }

    private fun show(track: MusicTrack?, playing: Boolean) {
        val s = session
        s?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track?.title ?: "Musik")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track?.artist.orEmpty())
                .build()
        )
        s?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1f
                )
                .build()
        )

        val style = Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2)
        s?.let { style.setMediaSession(it.sessionToken) }

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_n3x_music)
            .setContentTitle(track?.title ?: "Musik")
            .setContentText(track?.artist.orEmpty())
            .setShowWhen(false)
            .setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(R.drawable.ic_n3x_skip_previous, "Sebelumnya", ACTION_PREV, 1))
            .addAction(
                if (playing) action(R.drawable.ic_n3x_pause, "Jeda", ACTION_TOGGLE, 2)
                else action(R.drawable.ic_play_arrow_filled, "Putar", ACTION_TOGGLE, 2)
            )
            .addAction(action(R.drawable.ic_n3x_skip_next, "Berikutnya", ACTION_NEXT, 3))
            .addAction(action(R.drawable.ic_close, "Tutup", ACTION_CLOSE, 4))
            .setStyle(style)
            .build()

        if (!foreground) {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foreground = true
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }
}
