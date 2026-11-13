package com.irama.player.data

import android.app.RecoverableSecurityException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/** Menyimpan file audio baru ke penyimpanan bersama (folder Music). */
object MediaFiles {

    fun saveAudio(context: Context, file: File, baseName: String, relativePath: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, safeFileName(baseName) + ".m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Tidak bisa membuat file di folder Music")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Tidak bisa menulis file")
            out.use { o -> file.inputStream().use { it.copyTo(o, 256 * 1024) } }
            val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (ignored: Exception) {
            }
            throw e
        }
        return uri
    }

    fun safeFileName(s: String): String =
        s.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(100)
            .trimEnd('.', ' ')
            .ifBlank { "Audio" }

    /** Pesan error kalau nama folder tidak valid, atau null kalau boleh. */
    fun folderNameError(name: String): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Nama tidak boleh kosong"
            n == "." || n == ".." -> "Nama tidak valid"
            n.any { it in "\\/:*?\"<>|" } -> "Tidak boleh memakai tanda \\ / : * ? \" < > |"
            n.length > 60 -> "Maksimal 60 huruf"
            else -> null
        }
    }

    fun share(context: Context, song: Song) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = song.mime.ifBlank { "audio/*" }
            putExtra(Intent.EXTRA_STREAM, song.uri)
            clipData = ClipData.newRawUri(song.title, song.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Bagikan \"${song.title}\"").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Hapus, ganti nama, dan pindah folder lewat MediaStore. Untuk file milik aplikasi lain, Android
 * meminta persetujuan pengguna dulu (dialog sistem).
 */
object MediaOps {

    sealed interface Op {
        val songs: List<Song>
    }

    data class Delete(override val songs: List<Song>) : Op
    data class Rename(val song: Song, val newTitle: String) : Op {
        override val songs: List<Song> get() = listOf(song)
    }

    /** Pindahkan ke [relativePath], misalnya "Music/Santai/". */
    data class Move(override val songs: List<Song>, val relativePath: String, val label: String) : Op

    sealed interface Result {
        data class Done(val ok: Int, val failed: Int) : Result
        data class NeedsPermission(val sender: IntentSender) : Result
    }

    /** Android 11+: dialog izin sistem untuk operasi ini (sekaligus konfirmasi hapus). */
    fun permissionRequest(context: Context, op: Op): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uris = op.songs.map { it.uri }
        if (uris.isEmpty()) return null
        val resolver = context.contentResolver
        return when (op) {
            is Delete -> MediaStore.createDeleteRequest(resolver, uris).intentSender
            else -> MediaStore.createWriteRequest(resolver, uris).intentSender
        }
    }

    /**
     * Jalankan operasi. [systemDeleted] = true kalau dialog hapus Android 11+ sudah disetujui
     * (file sudah dihapus oleh sistem).
     */
    fun perform(context: Context, op: Op, systemDeleted: Boolean): Result {
        val resolver = context.contentResolver
        var ok = 0
        var failed = 0
        if (op is Rename) TitleOverrides.set(context, op.song.id, op.newTitle.trim())
        for (song in op.songs) {
            try {
                val changed = when (op) {
                    is Delete -> if (systemDeleted) 1 else resolver.delete(song.uri, null, null)
                    is Rename -> {
                        val values = ContentValues().apply {
                            put(MediaStore.Audio.Media.DISPLAY_NAME, MediaFiles.safeFileName(op.newTitle) + song.extension)
                        }
                        resolver.update(song.uri, values, null, null)
                    }
                    is Move -> {
                        if (song.folder == op.relativePath) 1 else {
                            val values = ContentValues().apply {
                                put(MediaStore.Audio.Media.RELATIVE_PATH, op.relativePath)
                            }
                            resolver.update(song.uri, values, null, null)
                        }
                    }
                }
                if (changed > 0) ok++ else failed++
            } catch (e: RecoverableSecurityException) {
                // Android 10: minta izin per file
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    return Result.NeedsPermission(e.userAction.actionIntent.intentSender)
                }
                failed++
            } catch (e: Exception) {
                failed++
            }
        }
        if (op is Delete && ok > 0) {
            val ids = op.songs.map { it.id }
            TitleOverrides.remove(context, ids)
            PlaylistStore.forget(ids)
        }
        return Result.Done(ok, failed)
    }

    /** Folder induk dari path, misalnya "Music/Irama/" → "Music/". Kosong kalau sudah paling atas. */
    fun parentOf(relativePath: String): String {
        val trimmed = relativePath.trimEnd('/')
        val idx = trimmed.lastIndexOf('/')
        return if (idx < 0) "" else trimmed.substring(0, idx + 1)
    }
}
