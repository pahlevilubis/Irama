package com.irama.player.audio

import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Timer tidur: hentikan musik otomatis setelah sekian menit. */
object SleepTimer {
    private val scope = MainScope()
    private var job: Job? = null

    private val _endsAt = MutableStateFlow<Long?>(null)
    /** Waktu berhenti (System.currentTimeMillis), atau null kalau timer mati. */
    val endsAt: StateFlow<Long?> = _endsAt.asStateFlow()

    /** Diisi oleh PlaybackService: aksi untuk menghentikan musik. */
    var onFire: (() -> Unit)? = null

    fun start(minutes: Int) {
        job?.cancel()
        val durationMs = minutes * 60_000L
        _endsAt.value = System.currentTimeMillis() + durationMs
        job = scope.launch {
            delay(durationMs)
            onFire?.invoke()
            _endsAt.value = null
            job = null
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _endsAt.value = null
    }
}
