package com.vidmax.app

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Dedicated Media3 playback screen. The player is created and released with
 * the Activity lifecycle so returning to the app cannot leave a stale surface.
 */
class PlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = intent.getStringExtra(EXTRA_MEDIA_URI)
        val uri = runCatching { Uri.parse(raw ?: "") }.getOrNull()
        if (uri == null || uri.scheme !in setOf("https", "http", "content", "file")) {
            Toast.makeText(this, "رابط الفيديو غير صالح", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        playerView = PlayerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            keepScreenOn = true
        }
        setContentView(FrameLayout(this).apply {
            addView(playerView, FrameLayout.LayoutParams(-1, -1))
        })
        initializePlayer(uri)
    }

    private fun initializePlayer(uri: Uri) {
        val instance = ExoPlayer.Builder(this).build()
        player = instance
        playerView.player = instance
        instance.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    "تعذر تشغيل الفيديو: تحقق من الرابط أو صيغة الملف",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
        instance.setMediaItem(MediaItem.fromUri(uri))
        instance.prepare()
        instance.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MEDIA_URI = "com.vidmax.app.extra.MEDIA_URI"
    }
}
