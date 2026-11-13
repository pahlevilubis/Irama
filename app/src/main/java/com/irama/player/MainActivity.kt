package com.irama.player

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.irama.player.download.ShareInbox
import com.irama.player.ui.IramaRoot
import com.irama.player.ui.IramaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // Tombol volume HP langsung mengatur volume musik
        volumeControlStream = AudioManager.STREAM_MUSIC
        // Link yang dibagikan dari YouTube (hanya saat pertama dibuka, bukan saat layar diputar)
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            IramaTheme {
                IramaRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("text/") != true) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        ShareInbox.offer(text)
    }
}
