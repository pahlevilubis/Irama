package com.irama.player.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.irama.player.data.LibraryEvents
import com.irama.player.data.MediaFiles
import com.irama.player.data.MusicRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import android.media.MediaFormat as AndroidMediaFormat

/**
 * Download audio dari link YouTube → file M4A (dengan judul, artis, cover) di folder Music/Irama.
 * Antrean diproses satu per satu di background.
 */
object YtDownloader {

    enum class Phase { QUEUED, READING, DOWNLOADING, SAVING, DONE, FAILED }

    data class Job(
        val id: Long,
        val url: String,
        val title: String = "",
        val channel: String = "",
        val phase: Phase = Phase.QUEUED,
        /** 0..1, atau -1 kalau belum diketahui. */
        val progress: Float = -1f,
        val message: String = "",
    ) {
        val active: Boolean get() = phase != Phase.DONE && phase != Phase.FAILED
    }

    private const val CHUNK = 1L shl 20 // 1 MB per permintaan
    private val POST_BODY = byteArrayOf(0x78, 0)
    private val URL_REGEX = Regex("""https?://\S+""")

    private val ids = AtomicLong(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Long>(Channel.UNLIMITED)

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    private lateinit var app: Context
    private lateinit var client: OkHttpClient

    @Volatile
    private var started = false

    fun init(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            app = context.applicationContext
            val http = NewPipeHttp(NewPipeHttp.createClient())
            client = http.client
            NewPipe.init(http, Localization("id", "ID"), ContentCountry("ID"))
            scope.launch {
                for (id in queue) process(id)
            }
            started = true
        }
    }

    /** Ambil link YouTube pertama dari teks (teks bagikan dari aplikasi YouTube sering berisi judul + link). */
    fun extractYoutubeUrl(text: String): String? =
        URL_REGEX.findAll(text)
            .map { it.value.trimEnd('.', ',', ')', ']', '"', '\'') }
            .firstOrNull { u ->
                val host = try {
                    URI(u).host?.lowercase(Locale.ROOT)
                } catch (e: Exception) {
                    null
                } ?: return@firstOrNull false
                host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
            }

    /** Tambah ke antrean. False kalau teks tidak berisi link YouTube. */
    fun enqueue(text: String): Boolean {
        val url = extractYoutubeUrl(text.trim()) ?: return false
        val id = ids.getAndIncrement()
        _jobs.update { listOf(Job(id, url, message = "Menunggu giliran…")) + it }
        queue.trySend(id)
        return true
    }

    fun retry(id: Long) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        if (job.phase != Phase.FAILED) return
        update(id) { it.copy(phase = Phase.QUEUED, progress = -1f, message = "Menunggu giliran…") }
        queue.trySend(id)
    }

    fun clearFinished() {
        _jobs.update { list -> list.filter { it.active } }
    }

    private fun update(id: Long, change: (Job) -> Job) {
        _jobs.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    // ---------- Proses satu link ----------

    private suspend fun process(id: Long) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        val work = File(app.cacheDir, "ytdl").apply { mkdirs() }
        val raw = File(work, "$id.raw")
        val remuxed = File(work, "$id.remux.m4a")
        val tagged = File(work, "$id.tagged.m4a")

        try {
            update(id) { it.copy(phase = Phase.READING, progress = -1f, message = "Membaca info video…") }
            val info = StreamInfo.getInfo(ServiceList.YouTube, job.url)
            val title = info.name?.takeIf { it.isNotBlank() } ?: "Audio YouTube"
            val channel = info.uploaderName.orEmpty().removeSuffix(" - Topic").trim()
            update(id) { it.copy(title = title, channel = channel) }

            val stream = pickAudio(info.audioStreams) ?: throw NoAudioException()
            val total = stream.itagItem?.contentLength?.takeIf { it > 0 } ?: -1L

            update(id) { it.copy(phase = Phase.DOWNLOADING, progress = 0f, message = "Mengunduh…") }
            downloadStream(stream.content, raw, total) { done ->
                val p = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
                val sizeText = if (total > 0) "${mb(done)} / ${mb(total)}" else mb(done)
                update(id) { it.copy(progress = p, message = "Mengunduh… $sizeText") }
            }

            update(id) { it.copy(phase = Phase.SAVING, progress = -1f, message = "Menyimpan & memasang cover…") }
            // Rapikan file (agar durasi & seek akurat), lalu pasang judul/artis/cover
            val clean = try {
                remux(raw, remuxed)
                remuxed
            } catch (e: Exception) {
                raw
            }
            val finalFile = if (clean === remuxed) {
                try {
                    val cover = fetchCover(info)
                    Mp4Tagger.tag(remuxed, tagged, Mp4Tagger.Tags(title, channel, "YouTube", cover))
                    tagged
                } catch (e: Exception) {
                    remuxed
                }
            } else {
                raw
            }

            MediaFiles.saveAudio(app, finalFile, title, MusicRepository.IRAMA_FOLDER)
            update(id) {
                it.copy(phase = Phase.DONE, progress = 1f, message = "Selesai • tersimpan di Music/Irama (${mb(finalFile.length())})")
            }
            LibraryEvents.notifyChanged()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            update(id) { it.copy(phase = Phase.FAILED, progress = -1f, message = friendlyError(e)) }
        } finally {
            raw.delete()
            remuxed.delete()
            tagged.delete()
        }
    }

    /** Pilih audio M4A (AAC) dengan bitrate tertinggi, utamakan trek suara asli. */
    private fun pickAudio(streams: List<AudioStream>): AudioStream? {
        val usable = streams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.M4A
        }
        if (usable.isEmpty()) return null
        val original = usable.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
        return original.ifEmpty { usable }.maxByOrNull { maxOf(it.averageBitrate, it.bitrate) }
    }

    // ---------- Unduh per potongan ----------

    private suspend fun downloadStream(url: String, dest: File, total: Long, onProgress: (Long) -> Unit) {
        var lastError: Exception? = null
        // Cara 1: seperti pemutar YouTube (POST + parameter range). Cara 2: GET + header Range.
        for (youtubeStyle in listOf(true, false)) {
            try {
                dest.delete()
                fetchChunks(url, dest, total, youtubeStyle, onProgress)
                val got = dest.length()
                if (got == 0L) throw IOException("Unduhan kosong")
                if (total > 0 && got < total * 9 / 10) throw IOException("Unduhan tidak lengkap")
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Unduhan gagal")
    }

    private suspend fun fetchChunks(
        url: String,
        dest: File,
        total: Long,
        youtubeStyle: Boolean,
        onProgress: (Long) -> Unit,
    ) {
        FileOutputStream(dest).use { out ->
            val buf = ByteArray(64 * 1024)
            var pos = 0L
            var rn = 1
            var lastReport = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val end = pos + CHUNK - 1
                val request = if (youtubeStyle) {
                    val sep = if ('?' in url) "&" else "?"
                    Request.Builder()
                        .url("$url${sep}range=$pos-$end&rn=$rn")
                        .post(POST_BODY.toRequestBody())
                        .header("User-Agent", mediaUserAgent(url))
                        .header("Accept-Encoding", "identity")
                        .apply {
                            if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
                                header("Origin", "https://www.youtube.com")
                                header("Referer", "https://www.youtube.com/")
                            }
                        }
                        .build()
                } else {
                    Request.Builder()
                        .url(url)
                        .get()
                        .header("User-Agent", NewPipeHttp.USER_AGENT)
                        .header("Range", "bytes=$pos-$end")
                        .header("Accept-Encoding", "identity")
                        .build()
                }
                rn++

                var got = 0L
                var wholeFile = false
                client.newCall(request).execute().use { resp ->
                    if (resp.code == 416) return // sudah sampai akhir file
                    if (!resp.isSuccessful) throw IOException("Server menolak (HTTP ${resp.code})")
                    if (resp.header("Content-Type").orEmpty().startsWith("text/")) {
                        throw IOException("Server mengirim halaman, bukan audio")
                    }
                    wholeFile = !youtubeStyle && resp.code == 200 // server mengabaikan Range
                    val body = resp.body ?: throw IOException("Respon kosong")
                    body.byteStream().use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            got += n
                            pos += n
                            if (pos - lastReport >= 256 * 1024) {
                                lastReport = pos
                                onProgress(pos)
                            }
                        }
                    }
                }
                onProgress(pos)
                if (wholeFile || got == 0L || got < CHUNK) return
                if (total > 0 && pos >= total) return
            }
        }
    }

    private fun mediaUserAgent(url: String): String =
        if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
            YoutubeParsingHelper.getVisionOsUserAgent(null)
        } else {
            NewPipeHttp.USER_AGENT
        }

    // ---------- Rapikan container (fMP4 → MP4 biasa) ----------

    private fun remux(src: File, dst: File) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(src.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(AndroidMediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("Tidak ada trek audio")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)

            val m = MediaMuxer(dst.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val outTrack = m.addTrack(format)
            m.start()

            val maxInput = if (format.containsKey(AndroidMediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(AndroidMediaFormat.KEY_MAX_INPUT_SIZE)
            } else 0
            val buffer = ByteBuffer.allocate(maxOf(maxInput, 512 * 1024))
            val info = MediaCodec.BufferInfo()
            var samples = 0
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else 0
                info.set(0, size, extractor.sampleTime, flags)
                m.writeSampleData(outTrack, buffer, info)
                samples++
                extractor.advance()
            }
            if (samples == 0) throw IOException("Audio kosong")
            m.stop()
        } finally {
            try {
                muxer?.release()
            } catch (e: Exception) {
                // abaikan
            }
            extractor.release()
        }
    }

    // ---------- Cover album dari thumbnail video ----------

    private fun fetchCover(info: StreamInfo): ByteArray? {
        return try {
            val url = info.thumbnails.maxByOrNull { it.height }?.url ?: return null
            val request = Request.Builder().url(url).header("User-Agent", NewPipeHttp.USER_AGENT).build()
            val bytes = client.newCall(request).execute().use { r ->
                if (r.isSuccessful) r.body?.bytes() else null
            } ?: return null
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val side = minOf(bmp.width, bmp.height)
            val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val scaled = if (side > 600) Bitmap.createScaledBitmap(square, 600, 600, true) else square
            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------- Utilitas ----------

    private class NoAudioException : IOException("no audio")

    private fun mb(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)

    private fun friendlyError(e: Throwable): String = when (e) {
        is NoAudioException ->
            "Video ini tidak punya audio yang bisa diunduh (mungkin siaran langsung)."
        is SignInConfirmNotBotException, is ReCaptchaException ->
            "YouTube minta verifikasi \"bukan robot\". Coba lagi nanti, atau ganti jaringan (WiFi ↔ data seluler)."
        is AgeRestrictedContentException ->
            "Video dibatasi umur, tidak bisa diunduh tanpa login."
        is GeographicRestrictionException ->
            "Video tidak tersedia di Indonesia."
        is PrivateContentException ->
            "Video ini privat."
        is PaidContentException, is YoutubeMusicPremiumContentException ->
            "Konten berbayar / khusus YouTube Premium."
        is ContentNotAvailableException ->
            "Video tidak tersedia atau sudah dihapus."
        is UnknownHostException, is ConnectException, is SocketTimeoutException ->
            "Koneksi internet bermasalah. Cek sinyal lalu coba lagi."
        is ExtractionException ->
            "Gagal membaca video. Kalau sering terjadi, YouTube mungkin berubah dan Irama perlu update."
        else ->
            "Gagal: ${e.message ?: e.javaClass.simpleName}"
    }
}
