package com.irama.player

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        setContent {
            IramaTheme {
                IramaRoot()
            }
        }
    }
}
