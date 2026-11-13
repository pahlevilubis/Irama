package com.irama.player.download

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Menulis tag judul, artis, album, dan cover (format iTunes / M4A) ke file MP4 audio.
 *
 * Hanya kotak `moov` yang dibaca ke memori; data audio (`mdat`) disalin apa adanya, jadi
 * aman untuk file besar. Kalau `moov` berada sebelum data audio, offset di `stco`/`co64`
 * digeser supaya file tetap valid.
 */
object Mp4Tagger {

    class Tags(
        val title: String?,
        val artist: String?,
        val album: String?,
        val coverJpeg: ByteArray?,
    )

    private class Box(val type: String, val start: Long, val size: Long, val headerSize: Int)

    private val CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl", "edts", "dinf")

    fun tag(src: File, dst: File, tags: Tags) {
        RandomAccessFile(src, "r").use { raf ->
            val fileLen = raf.length()
            val top = readBoxes(raf, 0, fileLen)
            val moov = top.firstOrNull { it.type == "moov" } ?: throw IOException("Kotak moov tidak ditemukan")
            if (moov.size > 64L * 1024 * 1024) throw IOException("moov terlalu besar")

            val moovBytes = ByteArray(moov.size.toInt())
            raf.seek(moov.start)
            raf.readFully(moovBytes)

            // Anak-anak moov, tanpa udta lama (diganti yang baru)
            val children = childBoxes(moovBytes, moov.headerSize, moovBytes.size)
                .filter { it.type != "udta" }
                .map { moovBytes.copyOfRange(it.start.toInt(), (it.start + it.size).toInt()) }

            val udta = buildUdta(tags)
            val newSize = 8L + children.sumOf { it.size.toLong() } + udta.size
            if (newSize > 0xFFFFFFFFL) throw IOException("moov terlalu besar")
            val delta = newSize - moov.size
            val moovEnd = moov.start + moov.size

            // Geser offset data audio yang letaknya sesudah moov
            if (delta != 0L) {
                for (child in children) patchOffsets(child, 0, child.size, moovEnd, delta)
            }

            FileOutputStream(dst).use { out ->
                copyRange(raf, out, 0, moov.start)
                out.write(u32(newSize))
                out.write("moov".toByteArray(Charsets.ISO_8859_1))
                for (child in children) out.write(child)
                out.write(udta)
                copyRange(raf, out, moovEnd, fileLen - moovEnd)
            }
        }
    }

    // ---------- Membaca struktur kotak ----------

    private fun readBoxes(raf: RandomAccessFile, from: Long, to: Long): List<Box> {
        val result = ArrayList<Box>()
        var pos = from
        val header = ByteArray(16)
        while (pos + 8 <= to) {
            raf.seek(pos)
            raf.readFully(header, 0, 8)
            var size = readU32(header, 0)
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            var headerSize = 8
            if (size == 1L) {
                raf.readFully(header, 8, 8)
                size = readU64(header, 8)
                headerSize = 16
            } else if (size == 0L) {
                size = to - pos
            }
            if (size < headerSize || pos + size > to) throw IOException("Struktur MP4 rusak di $type")
            result += Box(type, pos, size, headerSize)
            pos += size
        }
        return result
    }

    private fun childBoxes(buf: ByteArray, from: Int, to: Int): List<Box> {
        val result = ArrayList<Box>()
        var pos = from
        while (pos + 8 <= to) {
            var size = readU32(buf, pos)
            val type = String(buf, pos + 4, 4, Charsets.ISO_8859_1)
            var headerSize = 8
            if (size == 1L) {
                size = readU64(buf, pos + 8)
                headerSize = 16
            } else if (size == 0L) {
                size = (to - pos).toLong()
            }
            if (size < headerSize || pos + size > to) throw IOException("Struktur MP4 rusak di $type")
            result += Box(type, pos.toLong(), size, headerSize)
            pos += size.toInt()
        }
        return result
    }

    /** Telusuri trak/mdia/minf/stbl lalu geser isi stco/co64 yang menunjuk ke sesudah moov. */
    private fun patchOffsets(buf: ByteArray, from: Int, to: Int, threshold: Long, delta: Long) {
        for (box in childBoxes(buf, from, to)) {
            val start = box.start.toInt()
            val bodyStart = start + box.headerSize
            val end = (box.start + box.size).toInt()
            when (box.type) {
                in CONTAINERS -> patchOffsets(buf, bodyStart, end, threshold, delta)
                "stco" -> {
                    val count = readU32(buf, bodyStart + 4).toInt()
                    var p = bodyStart + 8
                    repeat(count) {
                        val v = readU32(buf, p)
                        if (v >= threshold) {
                            val nv = v + delta
                            if (nv < 0 || nv > 0xFFFFFFFFL) throw IOException("Offset stco di luar batas")
                            writeU32(buf, p, nv)
                        }
                        p += 4
                    }
                }
                "co64" -> {
                    val count = readU32(buf, bodyStart + 4).toInt()
                    var p = bodyStart + 8
                    repeat(count) {
                        val v = readU64(buf, p)
                        if (v >= threshold) writeU64(buf, p, v + delta)
                        p += 8
                    }
                }
            }
        }
    }

    // ---------- Membuat kotak metadata ----------

    private fun buildUdta(tags: Tags): ByteArray {
        val items = ByteArrayOutputStream()
        tags.title?.takeIf { it.isNotBlank() }?.let { items.write(textItem("©nam", it)) }
        tags.artist?.takeIf { it.isNotBlank() }?.let { items.write(textItem("©ART", it)) }
        tags.album?.takeIf { it.isNotBlank() }?.let { items.write(textItem("©alb", it)) }
        tags.coverJpeg?.takeIf { it.isNotEmpty() }?.let { items.write(dataItem("covr", 13, it)) }

        val hdlr = box(
            "hdlr",
            u32(0) + // version + flags
                u32(0) + // pre_defined
                "mdir".toByteArray(Charsets.ISO_8859_1) +
                "appl".toByteArray(Charsets.ISO_8859_1) + ByteArray(8) +
                ByteArray(1) // nama kosong
        )
        val ilst = box("ilst", items.toByteArray())
        val meta = box("meta", u32(0) + hdlr + ilst)
        return box("udta", meta)
    }

    private fun textItem(type: String, value: String): ByteArray =
        dataItem(type, 1, value.toByteArray(Charsets.UTF_8))

    private fun dataItem(type: String, dataType: Int, payload: ByteArray): ByteArray =
        box(type, box("data", u32(dataType.toLong()) + u32(0) + payload))

    private fun box(type: String, body: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
        require(typeBytes.size == 4) { "Tipe kotak harus 4 byte: $type" }
        return u32(8L + body.size) + typeBytes + body
    }

    // ---------- Utilitas byte ----------

    private fun copyRange(raf: RandomAccessFile, out: FileOutputStream, start: Long, length: Long) {
        if (length <= 0) return
        raf.seek(start)
        val buf = ByteArray(256 * 1024)
        var left = length
        while (left > 0) {
            val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) throw IOException("File berakhir lebih cepat")
            out.write(buf, 0, n)
            left -= n
        }
    }

    private fun u32(v: Long): ByteArray = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()
    )

    private fun readU32(b: ByteArray, p: Int): Long =
        ((b[p].toLong() and 0xFF) shl 24) or ((b[p + 1].toLong() and 0xFF) shl 16) or
            ((b[p + 2].toLong() and 0xFF) shl 8) or (b[p + 3].toLong() and 0xFF)

    private fun readU64(b: ByteArray, p: Int): Long = (readU32(b, p) shl 32) or readU32(b, p + 4)

    private fun writeU32(b: ByteArray, p: Int, v: Long) {
        b[p] = (v ushr 24).toByte()
        b[p + 1] = (v ushr 16).toByte()
        b[p + 2] = (v ushr 8).toByte()
        b[p + 3] = v.toByte()
    }

    private fun writeU64(b: ByteArray, p: Int, v: Long) {
        writeU32(b, p, v ushr 32)
        writeU32(b, p + 4, v and 0xFFFFFFFFL)
    }
}
