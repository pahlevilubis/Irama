package com.irama.player

import android.app.Application
import com.irama.player.audio.AudioFx

class IramaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AudioFx.init(this)
    }
}
