package com.irama.player.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Efek suara untuk hasil rekaman. Diproses setelah merekam (bukan real-time) supaya kualitasnya
 * bagus. Kotlin murni tanpa API Android, jadi bisa diuji di komputer.
 */
object VoiceFx {

    enum class Effect(val label: String, val desc: String) {
        ASLI("Asli", "Suara asli, dibersihkan dari dengung & desis"),
        MACHO("Macho", "Lebih berat, dalam, dan berwibawa"),
        MERDU("Merdu", "Jernih & lembut seperti di studio, enak buat nyanyi"),
        PENYIAR("Penyiar", "Tebal dan padat seperti penyiar radio FM"),
        GEMA("Gema", "Seperti bicara di aula besar"),
        RADIO_JADUL("Radio Jadul", "Seperti dari telepon atau radio lama"),
        ROBOT("Robot", "Suara mesin"),
        RAKSASA("Raksasa", "Super berat dan seram"),
        CHIPMUNK("Chipmunk", "Cempreng lucu"),
    }

    fun process(input: ShortArray, sampleRate: Int, effect: Effect): ShortArray {
        val sr = sampleRate
        var x = FloatArray(input.size) { input[it] / 32768f }
        dcBlock(x)
        when (effect) {
            Effect.ASLI -> {
                highPass(x, sr, 80.0)
                gate(x, sr, thresholdDb = -50f, rangeDb = -14f)
                compress(x, sr, thresholdDb = -20f, ratio = 2f, attackMs = 5f, releaseMs = 90f, makeupDb = 3f)
            }
            Effect.MACHO -> {
                highPass(x, sr, 70.0)
                gate(x, sr, thresholdDb = -50f, rangeDb = -14f)
                x = pitchShift(x, sr, -3.5)
                lowShelf(x, sr, 160.0, 4.5)
                peaking(x, sr, 350.0, 1.0, -1.5)
                peaking(x, sr, 2800.0, 1.0, 2.0)
                compress(x, sr, thresholdDb = -22f, ratio = 3f, attackMs = 5f, releaseMs = 100f, makeupDb = 4f)
                x = reverb(x, sr, roomSize = 0.35f, damp = 0.5f, wet = 0.10f, preDelayMs = 10f, tailSec = 0.8f)
            }
            Effect.MERDU -> {
                highPass(x, sr, 90.0)
                gate(x, sr, thresholdDb = -50f, rangeDb = -16f)
                peaking(x, sr, 280.0, 1.2, -2.5)
                peaking(x, sr, 4500.0, 0.8, 3.0)
                highShelf(x, sr, 10000.0, 2.5)
                compress(x, sr, thresholdDb = -24f, ratio = 3f, attackMs = 8f, releaseMs = 120f, makeupDb = 5f)
                x = reverb(x, sr, roomSize = 0.70f, damp = 0.45f, wet = 0.22f, preDelayMs = 20f, tailSec = 2f)
            }
            Effect.PENYIAR -> {
                highPass(x, sr, 60.0)
                gate(x, sr, thresholdDb = -48f, rangeDb = -18f)
                x = pitchShift(x, sr, -1.5)
                lowShelf(x, sr, 120.0, 5.0)
                peaking(x, sr, 3000.0, 1.0, 3.0)
                compress(x, sr, thresholdDb = -26f, ratio = 5f, attackMs = 3f, releaseMs = 80f, makeupDb = 7f)
                saturate(x, 1.5f)
            }
            Effect.GEMA -> {
                highPass(x, sr, 80.0)
                gate(x, sr, thresholdDb = -50f, rangeDb = -14f)
                compress(x, sr, thresholdDb = -20f, ratio = 2f, attackMs = 5f, releaseMs = 90f, makeupDb = 2f)
                x = echo(x, sr, delayMs = 320f, feedback = 0.35f, mix = 0.45f, tailSec = 1.5f)
                x = reverb(x, sr, roomSize = 0.92f, damp = 0.3f, wet = 0.35f, preDelayMs = 30f, tailSec = 3f)
            }
            Effect.RADIO_JADUL -> {
                highPass(x, sr, 400.0)
                highPass(x, sr, 400.0)
                lowPass(x, sr, 3200.0)
                lowPass(x, sr, 3200.0)
                peaking(x, sr, 1500.0, 1.0, 4.0)
                compress(x, sr, thresholdDb = -20f, ratio = 4f, attackMs = 3f, releaseMs = 60f, makeupDb = 6f)
                saturate(x, 3f)
            }
            Effect.ROBOT -> {
                highPass(x, sr, 100.0)
                ringMod(x, sr, 55.0)
                x = comb(x, sr, delayMs = 6f, feedback = 0.55f, mix = 0.6f)
                compress(x, sr, thresholdDb = -20f, ratio = 3f, attackMs = 3f, releaseMs = 80f, makeupDb = 4f)
            }
            Effect.RAKSASA -> {
                highPass(x, sr, 60.0)
                gate(x, sr, thresholdDb = -48f, rangeDb = -18f)
                x = pitchShift(x, sr, -8.0)
                lowShelf(x, sr, 120.0, 4.0)
                compress(x, sr, thresholdDb = -22f, ratio = 3f, attackMs = 5f, releaseMs = 120f, makeupDb = 4f)
                x = reverb(x, sr, roomSize = 0.75f, damp = 0.6f, wet = 0.2f, preDelayMs = 15f, tailSec = 2f)
            }
            Effect.CHIPMUNK -> {
                highPass(x, sr, 120.0)
                x = pitchShift(x, sr, 7.0)
                compress(x, sr, thresholdDb = -20f, ratio = 2.5f, attackMs = 3f, releaseMs = 60f, makeupDb = 3f)
            }
        }
        normalize(x, 0.89f)
        return ShortArray(x.size) { (x[it] * 32767f).roundToInt().coerceIn(-32768, 32767).toShort() }
    }

    // ---------- Filter (Biquad, rumus RBJ cookbook) ----------

    private fun biquad(x: FloatArray, b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
        val nb0 = b0 / a0
        val nb1 = b1 / a0
        val nb2 = b2 / a0
        val na1 = a1 / a0
        val na2 = a2 / a0
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in x.indices) {
            val xi = x[i].toDouble()
            val y = nb0 * xi + nb1 * x1 + nb2 * x2 - na1 * y1 - na2 * y2
            x2 = x1
            x1 = xi
            y2 = y1
            y1 = y
            x[i] = y.toFloat()
        }
    }

    fun highPass(x: FloatArray, sr: Int, f0: Double, q: Double = 0.7071) {
        val w0 = 2 * PI * f0 / sr
        val c = cos(w0)
        val alpha = sin(w0) / (2 * q)
        biquad(x, (1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    fun lowPass(x: FloatArray, sr: Int, f0: Double, q: Double = 0.7071) {
        val w0 = 2 * PI * f0 / sr
        val c = cos(w0)
        val alpha = sin(w0) / (2 * q)
        biquad(x, (1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    fun peaking(x: FloatArray, sr: Int, f0: Double, q: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40)
        val w0 = 2 * PI * f0 / sr
        val c = cos(w0)
        val alpha = sin(w0) / (2 * q)
        biquad(x, 1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
    }

    fun lowShelf(x: FloatArray, sr: Int, f0: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40)
        val w0 = 2 * PI * f0 / sr
        val c = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        biquad(
            x,
            a * ((a + 1) - (a - 1) * c + sa),
            2 * a * ((a - 1) - (a + 1) * c),
            a * ((a + 1) - (a - 1) * c - sa),
            (a + 1) + (a - 1) * c + sa,
            -2 * ((a - 1) + (a + 1) * c),
            (a + 1) + (a - 1) * c - sa
        )
    }

    fun highShelf(x: FloatArray, sr: Int, f0: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40)
        val w0 = 2 * PI * f0 / sr
        val c = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        biquad(
            x,
            a * ((a + 1) + (a - 1) * c + sa),
            -2 * a * ((a - 1) + (a + 1) * c),
            a * ((a + 1) + (a - 1) * c - sa),
            (a + 1) - (a - 1) * c + sa,
            2 * ((a - 1) - (a + 1) * c),
            (a + 1) - (a - 1) * c - sa
        )
    }

    private fun dcBlock(x: FloatArray) {
        var prevX = 0f
        var prevY = 0f
        for (i in x.indices) {
            val y = x[i] - prevX + 0.995f * prevY
            prevX = x[i]
            prevY = y
            x[i] = y
        }
    }

    // ---------- Dinamika ----------

    /** Kompresor: meratakan volume supaya suara terdengar padat dan jelas. */
    fun compress(
        x: FloatArray,
        sr: Int,
        thresholdDb: Float,
        ratio: Float,
        attackMs: Float,
        releaseMs: Float,
        makeupDb: Float,
        kneeDb: Float = 6f,
    ) {
        val atk = exp(-1.0 / (attackMs * 0.001 * sr)).toFloat()
        val rel = exp(-1.0 / (releaseMs * 0.001 * sr)).toFloat()
        val slope = 1f / ratio - 1f
        var env = 0f
        var gain = 1f
        for (i in x.indices) {
            val a = abs(x[i])
            env = if (a > env) atk * env + (1 - atk) * a else rel * env + (1 - rel) * a
            if (i and 7 == 0) { // hitung gain tiap 8 sampel supaya cepat
                val level = 20f * log10(env + 1e-9f)
                val over = level - thresholdDb
                val grDb = when {
                    2 * over < -kneeDb -> 0f
                    2 * abs(over) <= kneeDb -> slope * (over + kneeDb / 2).pow(2) / (2 * kneeDb)
                    else -> slope * over
                }
                gain = 10f.pow((grDb + makeupDb) / 20f)
            }
            x[i] *= gain
        }
    }

    /** Noise gate: mengecilkan desis/dengung di sela-sela kalimat. */
    fun gate(
        x: FloatArray,
        sr: Int,
        thresholdDb: Float,
        rangeDb: Float,
        attackMs: Float = 2f,
        releaseMs: Float = 150f,
        holdMs: Float = 80f,
    ) {
        val envCoef = exp(-1.0 / (0.010 * sr)).toFloat()
        val atk = exp(-1.0 / (attackMs * 0.001 * sr)).toFloat()
        val rel = exp(-1.0 / (releaseMs * 0.001 * sr)).toFloat()
        val floor = 10f.pow(rangeDb / 20f)
        val thr = 10f.pow(thresholdDb / 10f) // pembanding kuadrat
        val holdSamples = (holdMs * 0.001f * sr).toInt()
        var env = 0f
        var gain = floor
        var hold = 0
        for (i in x.indices) {
            val s = x[i] * x[i]
            env = envCoef * env + (1 - envCoef) * s
            val open = if (env > thr) {
                hold = holdSamples
                true
            } else if (hold > 0) {
                hold--
                true
            } else false
            val target = if (open) 1f else floor
            gain = if (target > gain) atk * gain + (1 - atk) * target else rel * gain + (1 - rel) * target
            x[i] *= gain
        }
    }

    private fun saturate(x: FloatArray, drive: Float) {
        val norm = tanh(drive)
        for (i in x.indices) x[i] = tanh(drive * x[i]) / norm
    }

    fun normalize(x: FloatArray, peak: Float) {
        var m = 0f
        for (v in x) {
            val a = abs(v)
            if (a > m) m = a
        }
        if (m < 1e-6f) return
        val g = peak / m
        for (i in x.indices) x[i] *= g
    }

    // ---------- Ruang & modulasi ----------

    private class Comb(size: Int) {
        private val buf = FloatArray(max(1, size))
        private var idx = 0
        private var store = 0f
        fun process(input: Float, feedback: Float, damp1: Float, damp2: Float): Float {
            val out = buf[idx]
            store = out * damp2 + store * damp1
            buf[idx] = input + store * feedback
            if (++idx >= buf.size) idx = 0
            return out
        }
    }

    private class AllPass(size: Int) {
        private val buf = FloatArray(max(1, size))
        private var idx = 0
        fun process(input: Float): Float {
            val b = buf[idx]
            buf[idx] = input + b * 0.5f
            if (++idx >= buf.size) idx = 0
            return b - input
        }
    }

    /** Reverb ala Freeverb (mono). Hasilnya lebih panjang [tailSec] detik supaya gema tidak terpotong. */
    fun reverb(
        x: FloatArray,
        sr: Int,
        roomSize: Float,
        damp: Float,
        wet: Float,
        preDelayMs: Float,
        tailSec: Float,
    ): FloatArray {
        val scale = sr / 44100.0
        val combs = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617).map { Comb((it * scale).toInt()) }
        val allPasses = intArrayOf(556, 441, 341, 225).map { AllPass((it * scale).toInt()) }
        val feedback = roomSize * 0.28f + 0.7f
        val damp1 = damp * 0.4f
        val damp2 = 1f - damp1
        val pre = (preDelayMs * 0.001f * sr).toInt()
        val outLen = x.size + (tailSec * sr).toInt()
        val y = FloatArray(outLen)
        for (i in 0 until outLen) {
            val dry = if (i < x.size) x[i] else 0f
            val src = i - pre
            val inp = (if (src in x.indices) x[src] else 0f) * 0.015f
            var acc = 0f
            for (c in combs) acc += c.process(inp, feedback, damp1, damp2)
            for (a in allPasses) acc = a.process(acc)
            y[i] = dry + acc * wet * 3f
        }
        return trimSilence(y, x.size)
    }

    fun echo(x: FloatArray, sr: Int, delayMs: Float, feedback: Float, mix: Float, tailSec: Float): FloatArray {
        val d = max(1, (delayMs * 0.001f * sr).toInt())
        val buf = FloatArray(d)
        var idx = 0
        val outLen = x.size + (tailSec * sr).toInt()
        val y = FloatArray(outLen)
        for (i in 0 until outLen) {
            val dry = if (i < x.size) x[i] else 0f
            val delayed = buf[idx]
            buf[idx] = dry + delayed * feedback
            if (++idx >= d) idx = 0
            y[i] = dry + delayed * mix
        }
        return trimSilence(y, x.size)
    }

    private fun comb(x: FloatArray, sr: Int, delayMs: Float, feedback: Float, mix: Float): FloatArray {
        val d = max(1, (delayMs * 0.001f * sr).toInt())
        val buf = FloatArray(d)
        var idx = 0
        val y = FloatArray(x.size)
        for (i in x.indices) {
            val delayed = buf[idx]
            buf[idx] = x[i] + delayed * feedback
            if (++idx >= d) idx = 0
            y[i] = x[i] * (1 - mix) + delayed * mix
        }
        return y
    }

    private fun ringMod(x: FloatArray, sr: Int, freq: Double) {
        val w = 2 * PI * freq / sr
        for (i in x.indices) x[i] = (x[i] * sin(w * i)).toFloat()
    }

    /** Buang ekor yang benar-benar sunyi (tapi tidak lebih pendek dari panjang asli). */
    private fun trimSilence(y: FloatArray, minLen: Int): FloatArray {
        var end = y.size
        while (end > minLen && abs(y[end - 1]) < 1e-4f) end--
        return if (end == y.size) y else y.copyOf(end)
    }

    // ---------- Ubah nada (pitch shift) tanpa mengubah durasi ----------

    /**
     * Geser nada sebesar [semitones] (negatif = lebih berat). Caranya: sinyal di-resample (nada &
     * durasi berubah), lalu durasinya dikembalikan dengan WSOLA (time-stretch yang menjaga kualitas suara).
     */
    fun pitchShift(x: FloatArray, sr: Int, semitones: Double): FloatArray {
        if (abs(semitones) < 0.01 || x.size < sr / 10) return x
        val p = 2.0.pow(semitones / 12.0)
        val src = if (p > 1.0) {
            // Anti-aliasing sebelum nada dinaikkan
            val cut = sr / 2.0 / p * 0.9
            x.copyOf().also {
                lowPass(it, sr, cut)
                lowPass(it, sr, cut)
            }
        } else x
        val resampled = resample(src, p)
        return wsola(resampled, sr, x.size)
    }

    /** Baca sinyal dengan langkah [step] (step > 1 → lebih pendek & nada naik). Interpolasi kubik Hermite. */
    private fun resample(x: FloatArray, step: Double): FloatArray {
        val outLen = floor((x.size - 1) / step).toInt().coerceAtLeast(1)
        val y = FloatArray(outLen)
        val last = x.size - 1
        for (i in 0 until outLen) {
            val t = i * step
            val i0 = t.toInt()
            val f = (t - i0).toFloat()
            val xm1 = x[max(0, i0 - 1)]
            val x0 = x[min(last, i0)]
            val x1 = x[min(last, i0 + 1)]
            val x2 = x[min(last, i0 + 2)]
            val c1 = 0.5f * (x1 - xm1)
            val c2 = xm1 - 2.5f * x0 + 2f * x1 - 0.5f * x2
            val c3 = 0.5f * (x2 - xm1) + 1.5f * (x0 - x1)
            y[i] = ((c3 * f + c2) * f + c1) * f + x0
        }
        return y
    }

    /** WSOLA: ubah panjang [x] menjadi [targetLength] sampel tanpa mengubah nada. */
    fun wsola(x: FloatArray, sr: Int, targetLength: Int): FloatArray {
        var n = (sr * 0.030).toInt()
        if (n % 2 == 1) n++
        val hs = n / 2
        val ha = hs * x.size.toDouble() / targetLength
        val tol = (sr * 0.010).toInt()
        val cmpLen = n / 2
        val window = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / n)).toFloat() }

        val out = FloatArray(targetLength + n)
        val norm = FloatArray(targetLength + n)
        var prevPos = 0
        var k = 0
        while (true) {
            val outPos = k * hs
            if (outPos >= targetLength) break
            val nominal = (k * ha).roundToInt()
            val pos = if (k == 0) 0 else bestMatch(x, prevPos + hs, nominal, tol, cmpLen)
            for (j in 0 until n) {
                val xi = pos + j
                val v = if (xi >= 0 && xi < x.size) x[xi] else 0f
                out[outPos + j] += v * window[j]
                norm[outPos + j] += window[j]
            }
            prevPos = pos
            k++
        }
        val y = FloatArray(targetLength)
        for (i in 0 until targetLength) {
            val w = norm[i]
            y[i] = if (w > 0.05f) out[i] / w else out[i]
        }
        return y
    }

    /** Cari posisi di sekitar [nominal] yang paling mirip dengan kelanjutan alami frame sebelumnya. */
    private fun bestMatch(x: FloatArray, natural: Int, nominal: Int, tol: Int, len: Int): Int {
        val maxStart = x.size - len - 1
        if (maxStart <= 0) return nominal.coerceIn(0, max(0, x.size - 1))
        val lo = max(0, nominal - tol)
        val hi = min(maxStart, nominal + tol)
        if (hi <= lo || natural < 0 || natural + len >= x.size) return nominal.coerceIn(0, maxStart)

        fun score(c: Int, step: Int): Double {
            var dot = 0.0
            var energy = 0.0
            var j = 0
            while (j < len) {
                val v = x[c + j]
                dot += x[natural + j] * v
                energy += v * v
                j += step
            }
            return dot / sqrt(energy + 1e-9)
        }

        // Pencarian kasar lalu dihaluskan
        var best = lo
        var bestScore = Double.NEGATIVE_INFINITY
        var c = lo
        while (c <= hi) {
            val s = score(c, 4)
            if (s > bestScore) {
                bestScore = s
                best = c
            }
            c += 4
        }
        val rlo = max(lo, best - 4)
        val rhi = min(hi, best + 4)
        bestScore = Double.NEGATIVE_INFINITY
        for (c2 in rlo..rhi) {
            val s = score(c2, 1)
            if (s > bestScore) {
                bestScore = s
                best = c2
            }
        }
        return best
    }
}
