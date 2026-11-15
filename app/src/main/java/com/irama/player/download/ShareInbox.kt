package com.irama.player.download

import android.net.Uri
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

/** Menampung file audio yang dibuka lewat "Buka dengan → Irama". */
object OpenInbox {
    private val _pending = MutableStateFlow<Uri?>(null)
    val pending: StateFlow<Uri?> = _pending.asStateFlow()

    fun offer(uri: Uri) {
        _pending.value = uri
    }

    fun consume() {
        _pending.value = null
    }
}
