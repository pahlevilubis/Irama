package com.irama.player

import android.app.Application
import com.irama.player.audio.AudioFx
import com.irama.player.download.YtDownloader

class IramaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AudioFx.init(this)
        YtDownloader.init(this)
    }
}
