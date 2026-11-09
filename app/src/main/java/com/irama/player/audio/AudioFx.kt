package com.irama.player.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Pengelola efek suara: Equalizer, Bass Boost, Surround 3D (Virtualizer),
 * dan Penguat Volume (LoudnessEnhancer). Pengaturan disimpan permanen.
 */
object AudioFx {

    data class Preset(val name: String, val gainsDb: List<Float>)

    data class State(
        val enabled: Boolean = true,
        val preset: String = "Normal",
        val bandFreqsHz: List<Int> = DEFAULT_FREQS,
        val bandLevels: List<Int> = List(DEFAULT_FREQS.size) { 0 }, // millibel (100 = 1 dB)
        val minLevel: Int = -1500,
        val maxLevel: Int = 1500,
        val bass: Int = 0,      // 0..1000
        val surround: Int = 0,  // 0..1000
        val loudness: Int = 0,  // 0..1000 millibel
        val eqSupported: Boolean = true,
        val bassSupported: Boolean = true,
        val surroundSupported: Boolean = true,
        val loudnessSupported: Boolean = true,
    )

    const val CUSTOM = "Kustom"

    private val DEFAULT_FREQS = listOf(60, 230, 910, 3600, 14000)
    private val REF_FREQS = listOf(60f, 230f, 910f, 3600f, 14000f)

    /** Gain (dB) pada 60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz. */
    val presets: List<Preset> = listOf(
        Preset("Normal", listOf(0f, 0f, 0f, 0f, 0f)),
        Preset("Bass Mantap", listOf(6f, 4f, 0f, 0f, 1f)),
        Preset("Pop", listOf(-1f, 2f, 4f, 2f, -1f)),
        Preset("Rock", listOf(5f, 3f, -1f, 3f, 5f)),
        Preset("Dangdut", listOf(4f, 5f, 0f, 3f, 2f)),
        Preset("Jazz", listOf(4f, 2f, -2f, 2f, 5f)),
        Preset("Klasik", listOf(5f, 3f, -2f, 4f, 4f)),
        Preset("Vokal", listOf(-2f, -1f, 3f, 4f, 1f)),
        Preset("Hip-Hop", listOf(5f, 3f, 0f, 1f, 3f)),
        Preset("Elektronik", listOf(6f, 2f, 0f, 3f, 5f)),
        Preset("Akustik", listOf(3f, 1f, 2f, 3f, 2f)),
        Preset("Treble", listOf(-1f, 0f, 1f, 4f, 6f)),
        Preset("Santai", listOf(2f, 1f, 0f, -2f, -3f)),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var sessionId = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("audio_fx", Context.MODE_PRIVATE)
        prefs = p
        val freqs = p.getString("freqs", null).toIntList()?.takeIf { it.isNotEmpty() } ?: DEFAULT_FREQS
        val min = p.getInt("min", -1500)
        val max = p.getInt("max", 1500)
        val preset = p.getString("preset", "Normal") ?: "Normal"
        val saved = p.getString("levels", null).toIntList()
        val levels = if (saved != null && saved.size == freqs.size) saved
        else levelsFor(presetNamed(preset) ?: presets[0], freqs, min, max)
        _state.value = State(
            enabled = p.getBoolean("enabled", true),
            preset = if (saved != null && saved.size == freqs.size) preset else (presetNamed(preset)?.name ?: "Normal"),
            bandFreqsHz = freqs,
            bandLevels = levels,
            minLevel = min,
            maxLevel = max,
            bass = p.getInt("bass", 0),
            surround = p.getInt("surround", 0),
            loudness = p.getInt("loudness", 0),
        )
    }

    /** Pasang efek ke sesi audio pemutar. */
    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (audioSessionId == sessionId && equalizer != null) return
        releaseEffects()
        sessionId = audioSessionId

        var s = _state.value

        val eq = try { Equalizer(0, audioSessionId) } catch (e: Throwable) { null }
        equalizer = eq
        s = if (eq != null) {
            val n = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val min = range[0].toInt()
            val max = range[1].toInt()
            val freqs = (0 until n).map { eq.getCenterFreq(it.toShort()) / 1000 }
            val keepCustom = s.preset == CUSTOM && s.bandLevels.size == n
            val levels = if (keepCustom) s.bandLevels.map { it.coerceIn(min, max) }
            else levelsFor(presetNamed(s.preset) ?: presets[0], freqs, min, max)
            s.copy(
                bandFreqsHz = freqs,
                bandLevels = levels,
                minLevel = min,
                maxLevel = max,
                preset = if (keepCustom) CUSTOM else (presetNamed(s.preset)?.name ?: "Normal"),
                eqSupported = true,
            )
        } else {
            s.copy(eqSupported = false)
        }

        val bb = try { BassBoost(0, audioSessionId) } catch (e: Throwable) { null }
        bassBoost = bb
        val vz = try { Virtualizer(0, audioSessionId) } catch (e: Throwable) { null }
        virtualizer = vz
        val le = try { LoudnessEnhancer(audioSessionId) } catch (e: Throwable) { null }
        loudness = le

        s = s.copy(
            bassSupported = bb?.strengthSupported == true,
            surroundSupported = vz?.strengthSupported == true,
            loudnessSupported = le != null,
        )
        _state.value = s
        applyAll()
        save()
    }

    fun release() {
        releaseEffects()
    }

    // ---------- Aksi dari UI ----------

    fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }

    fun selectPreset(p: Preset) = update {
        it.copy(preset = p.name, bandLevels = levelsFor(p, it.bandFreqsHz, it.minLevel, it.maxLevel))
    }

    fun setBand(index: Int, level: Int) = update { s ->
        if (index !in s.bandLevels.indices || s.bandLevels[index] == level) s
        else s.copy(
            preset = CUSTOM,
            bandLevels = s.bandLevels.toMutableList().also { it[index] = level.coerceIn(s.minLevel, s.maxLevel) }
        )
    }

    fun setBass(value: Int) = update { it.copy(bass = value.coerceIn(0, 1000)) }
    fun setSurround(value: Int) = update { it.copy(surround = value.coerceIn(0, 1000)) }
    fun setLoudness(value: Int) = update { it.copy(loudness = value.coerceIn(0, 1000)) }

    fun reset() = update {
        it.copy(
            enabled = true,
            preset = "Normal",
            bandLevels = List(it.bandFreqsHz.size) { 0 },
            bass = 0,
            surround = 0,
            loudness = 0,
        )
    }

    // ---------- Internal ----------

    private inline fun update(change: (State) -> State) {
        val old = _state.value
        val new = change(old)
        if (new == old) return
        _state.value = new
        applyAll()
        save()
    }

    private fun applyAll() {
        val s = _state.value
        equalizer?.let { eq ->
            safely {
                val bands = eq.numberOfBands.toInt()
                s.bandLevels.forEachIndexed { i, lv ->
                    if (i < bands) eq.setBandLevel(i.toShort(), lv.coerceIn(s.minLevel, s.maxLevel).toShort())
                }
                eq.setEnabled(s.enabled)
            }
        }
        bassBoost?.let { bb ->
            safely {
                if (bb.strengthSupported) bb.setStrength(s.bass.toShort())
                bb.setEnabled(s.enabled && s.bass > 0)
            }
        }
        virtualizer?.let { vz ->
            safely {
                if (vz.strengthSupported) vz.setStrength(s.surround.toShort())
                vz.setEnabled(s.enabled && s.surround > 0)
            }
        }
        loudness?.let { le ->
            safely {
                le.setTargetGain(s.loudness)
                le.setEnabled(s.enabled && s.loudness > 0)
            }
        }
    }

    private fun save() {
        val s = _state.value
        prefs?.edit {
            putBoolean("enabled", s.enabled)
            putString("preset", s.preset)
            putString("levels", s.bandLevels.joinToString(","))
            putString("freqs", s.bandFreqsHz.joinToString(","))
            putInt("min", s.minLevel)
            putInt("max", s.maxLevel)
            putInt("bass", s.bass)
            putInt("surround", s.surround)
            putInt("loudness", s.loudness)
        }
    }

    private fun releaseEffects() {
        val all: List<AudioEffect?> = listOf(equalizer, bassBoost, virtualizer, loudness)
        all.forEach { fx -> safely { fx?.release() } }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudness = null
        sessionId = 0
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            // Sebagian HP membatasi efek tertentu; abaikan supaya musik tetap jalan
        }
    }

    private fun presetNamed(name: String): Preset? = presets.firstOrNull { it.name == name }

    private fun levelsFor(p: Preset, freqs: List<Int>, min: Int, max: Int): List<Int> =
        freqs.map { f -> (gainAt(p.gainsDb, f) * 100f).roundToInt().coerceIn(min, max) }

    /** Interpolasi gain preset ke frekuensi band milik HP (skala logaritmik). */
    private fun gainAt(gains: List<Float>, freqHz: Int): Float {
        val x = log10(freqHz.coerceAtLeast(1).toFloat())
        val xs = REF_FREQS.map { log10(it) }
        if (x <= xs.first()) return gains.first()
        if (x >= xs.last()) return gains.last()
        for (i in 0 until xs.size - 1) {
            if (x <= xs[i + 1]) {
                val t = (x - xs[i]) / (xs[i + 1] - xs[i])
                return gains[i] + t * (gains[i + 1] - gains[i])
            }
        }
        return gains.last()
    }

    private fun String?.toIntList(): List<Int>? =
        this?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
}
