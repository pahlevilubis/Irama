package com.irama.player.ui

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.irama.player.audio.AacEncoder
import com.irama.player.audio.VoiceFx
import com.irama.player.data.LibraryEvents
import com.irama.player.data.MediaFiles
import com.irama.player.data.MusicRepository
import com.irama.player.download.Mp4Tagger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Perekam suara: rekam → pilih efek (Macho, Merdu, …) → dengarkan → simpan ke Music/Irama/Rekaman. */
class RecorderViewModel(app: Application) : AndroidViewModel(app) {

    enum class Phase { IDLE, RECORDING, PROCESSING, READY, SAVING }

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val elapsedMs: Long = 0L,
        val levels: List<Float> = emptyList(),
        val effect: VoiceFx.Effect = VoiceFx.Effect.ASLI,
        val durationMs: Long = 0L,
        val previewing: Boolean = false,
        val previewProgress: Float = 0f,
        val message: String? = null,
        val error: String? = null,
    )

    companion object {
        const val SR = 44100
        const val MAX_SECONDS = 6 * 60
        private const val LEVEL_BARS = 48
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    @Volatile
    private var stopRequested = false
    private var raw: ShortArray? = null
    private var processed: ShortArray? = null
    private var processedEffect: VoiceFx.Effect? = null
    private var processJob: Job? = null
    private var previewJob: Job? = null

    // ---------- Rekam ----------

    @SuppressLint("MissingPermission") // izin dicek di layar sebelum memanggil ini
    fun startRecording() {
        val phase = _ui.value.phase
        if (phase == Phase.RECORDING || phase == Phase.SAVING) return
        stopPreview()
        processJob?.cancel()
        raw = null
        processed = null
        processedEffect = null

        val minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = if (minBuf <= 0) null else try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SR,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf * 2, SR)
            )
        } catch (e: Exception) {
            null
        }
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            rec?.release()
            _ui.update { it.copy(error = "Mikrofon tidak bisa dipakai. Tutup aplikasi lain yang sedang merekam.") }
            return
        }

        stopRequested = false
        _ui.update {
            it.copy(phase = Phase.RECORDING, elapsedMs = 0L, levels = emptyList(), durationMs = 0L, message = null, error = null)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val chunks = ArrayList<ShortArray>()
            val buf = ShortArray(2048)
            var total = 0
            val levels = ArrayDeque<Float>()
            var failed: String? = null
            try {
                rec.startRecording()
                while (!stopRequested && isActive) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n < 0) {
                        failed = "Gagal membaca mikrofon (kode $n)"
                        break
                    }
                    if (n == 0) continue
                    chunks += buf.copyOf(n)
                    total += n
                    var sum = 0.0
                    for (i in 0 until n) {
                        val v = buf[i].toDouble()
                        sum += v * v
                    }
                    val db = 20 * log10(sqrt(sum / n) / 32768.0 + 1e-9)
                    levels.addLast(((db + 60) / 60).toFloat().coerceIn(0f, 1f))
                    if (levels.size > LEVEL_BARS) levels.removeFirst()
                    val elapsed = total * 1000L / SR
                    val snapshot = levels.toList()
                    _ui.update { it.copy(elapsedMs = elapsed, levels = snapshot) }
                    if (total >= SR * MAX_SECONDS) break
                }
            } catch (e: Exception) {
                failed = "Rekaman gagal: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                try {
                    rec.stop()
                } catch (e: Exception) {
                    // abaikan
                }
                rec.release()
            }

            if (failed != null || total < SR / 2) {
                _ui.update {
                    it.copy(phase = Phase.IDLE, levels = emptyList(), elapsedMs = 0L, error = failed ?: "Rekaman terlalu pendek")
                }
                return@launch
            }
            val all = ShortArray(total)
            var p = 0
            for (c in chunks) {
                c.copyInto(all, p)
                p += c.size
            }
            chunks.clear()
            raw = all
            applyEffect(_ui.value.effect, autoplay = false)
        }
    }

    fun stopRecording() {
        stopRequested = true
    }

    // ---------- Efek ----------

    fun selectEffect(effect: VoiceFx.Effect) {
        _ui.update { it.copy(effect = effect) }
        if (raw != null && _ui.value.phase != Phase.SAVING) applyEffect(effect, autoplay = true)
    }

    private fun applyEffect(effect: VoiceFx.Effect, autoplay: Boolean) {
        val src = raw ?: return
        stopPreview()
        processJob?.cancel()
        _ui.update { it.copy(phase = Phase.PROCESSING, error = null) }
        processJob = viewModelScope.launch(Dispatchers.Default) {
            val out = try {
                VoiceFx.process(src, SR, effect)
            } catch (t: Throwable) {
                null
            }
            ensureActive()
            if (out == null) {
                processed = src
                processedEffect = VoiceFx.Effect.ASLI
                _ui.update {
                    it.copy(phase = Phase.READY, durationMs = src.size * 1000L / SR, error = "Efek gagal diproses, memakai suara asli")
                }
                return@launch
            }
            processed = out
            processedEffect = effect
            _ui.update { it.copy(phase = Phase.READY, durationMs = out.size * 1000L / SR) }
            if (autoplay) playPreview()
        }
    }

    // ---------- Dengarkan hasil ----------

    fun togglePreview() {
        if (_ui.value.previewing) stopPreview() else playPreview()
    }

    private fun playPreview() {
        val pcm = processed ?: return
        stopPreview()
        previewJob = viewModelScope.launch(Dispatchers.IO) {
            val minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SR)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(max(minBuf, SR))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (e: Exception) {
                null
            } ?: return@launch

            _ui.update { it.copy(previewing = true, previewProgress = 0f) }
            try {
                track.play()
                var pos = 0
                while (isActive && pos < pcm.size) {
                    val n = min(4096, pcm.size - pos)
                    val w = track.write(pcm, pos, n)
                    if (w <= 0) break
                    pos += w
                    val progress = pos.toFloat() / pcm.size
                    _ui.update { it.copy(previewProgress = progress) }
                }
                if (isActive) delay(600) // tunggu sisa buffer selesai diputar
            } finally {
                try {
                    track.pause()
                    track.flush()
                } catch (e: Exception) {
                    // abaikan
                }
                track.release()
                _ui.update { it.copy(previewing = false, previewProgress = 0f) }
            }
        }
    }

    private fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
    }

    // ---------- Simpan / buang ----------

    fun save() {
        val pcm = processed ?: return
        val effect = processedEffect ?: VoiceFx.Effect.ASLI
        stopPreview()
        _ui.update { it.copy(phase = Phase.SAVING, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val dir = File(ctx.cacheDir, "rec").apply { mkdirs() }
            val encoded = File(dir, "rec.m4a")
            val tagged = File(dir, "rec_tagged.m4a")
            try {
                AacEncoder.encode(pcm, SR, encoded)
                val stamp = SimpleDateFormat("d MMM yyyy HH.mm", Locale.forLanguageTag("id-ID")).format(Date())
                val title = if (effect == VoiceFx.Effect.ASLI) "Rekaman $stamp" else "Rekaman $stamp (${effect.label})"
                val finalFile = try {
                    Mp4Tagger.tag(encoded, tagged, Mp4Tagger.Tags(title, APP_CREATOR, "Rekaman Irama", recordingCover(effect.label)))
                    tagged
                } catch (e: Exception) {
                    encoded
                }
                MediaFiles.saveAudio(ctx, finalFile, title, MusicRepository.IRAMA_FOLDER + "Rekaman/")
                LibraryEvents.notifyChanged()
                raw = null
                processed = null
                processedEffect = null
                _ui.update { UiState(effect = it.effect, message = "Tersimpan: $title") }
            } catch (e: Exception) {
                _ui.update { it.copy(phase = Phase.READY, error = "Gagal menyimpan: ${e.message ?: e.javaClass.simpleName}") }
            } finally {
                encoded.delete()
                tagged.delete()
            }
        }
    }

    fun discard() {
        stopPreview()
        processJob?.cancel()
        raw = null
        processed = null
        processedEffect = null
        _ui.update { UiState(effect = it.effect) }
    }

    fun clearMessages() {
        _ui.update { it.copy(message = null, error = null) }
    }

    /** Layar ditutup: hentikan rekaman (hasilnya tetap ada) dan preview. */
    fun onScreenClosed() {
        if (_ui.value.phase == Phase.RECORDING) stopRecording()
        stopPreview()
    }

    override fun onCleared() {
        stopRequested = true
        stopPreview()
        super.onCleared()
    }

    /** Cover sederhana untuk file rekaman: gradasi + ikon mikrofon + nama efek. */
    private fun recordingCover(label: String): ByteArray? = try {
        val size = 600f
        val bmp = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, size, size, 0xFFFF7A6B.toInt(), 0xFFFFB547.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size, size, paint)
        paint.shader = null

        val ink = 0xFF2B1A00.toInt()
        paint.color = ink
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(245f, 100f, 355f, 310f), 55f, 55f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 22f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(RectF(195f, 150f, 405f, 370f), 0f, 180f, false, paint)
        canvas.drawLine(300f, 370f, 300f, 430f, paint)
        canvas.drawLine(240f, 440f, 360f, 440f, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 58f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(label.uppercase(Locale.ROOT), size / 2, 530f, paint)

        ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    } catch (e: Exception) {
        null
    }
}
