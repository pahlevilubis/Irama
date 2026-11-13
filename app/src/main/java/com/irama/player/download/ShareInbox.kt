package com.irama.player.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Menampung teks yang dibagikan ke Irama (misalnya link dari aplikasi YouTube). */
object ShareInbox {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(text: String) {
        _pending.value = text
    }

    fun consume() {
        _pending.value = null
    }
}
