package com.irama.player.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import kotlin.math.min

/** Mengubah PCM 16-bit mono menjadi file M4A (AAC) memakai encoder bawaan HP. */
object AacEncoder {

    fun encode(pcm: ShortArray, sampleRate: Int, out: File, bitRate: Int = 128_000) {
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val format = MediaFormat.createAudioFormat(mime, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(mime)
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxing = false
        var written = 0
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var pos = 0
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex) ?: throw IOException("Buffer encoder kosong")
                        buf.clear()
                        val count = min(buf.capacity() / 2, pcm.size - pos)
                        val ptsUs = pos * 1_000_000L / sampleRate
                        if (count <= 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.order(ByteOrder.nativeOrder()).asShortBuffer().put(pcm, pos, count)
                            codec.queueInputBuffer(inIndex, 0, count * 2, ptsUs, 0)
                            pos += count
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxing = true
                } else if (outIndex >= 0) {
                    val ob = codec.getOutputBuffer(outIndex)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (ob != null && info.size > 0 && muxing) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        muxer.writeSampleData(track, ob, info)
                        written++
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            if (!muxing || written == 0) throw IOException("Encoder tidak menghasilkan audio")
            muxer.stop()
        } finally {
            try {
                codec.stop()
            } catch (e: Exception) {
                // abaikan
            }
            codec.release()
            try {
                muxer.release()
            } catch (e: Exception) {
                // abaikan
            }
        }
    }
}
