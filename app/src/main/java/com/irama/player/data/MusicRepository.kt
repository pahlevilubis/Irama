package com.irama.player.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

/** Membaca semua lagu yang tersimpan di HP lewat MediaStore. */
object MusicRepository {

    /** Lagu lebih pendek dari ini (voice note, nada dering) diabaikan. */
    private const val MIN_DURATION_MS = 30_000L

    fun loadSongs(context: Context): List<Song> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.TRACK,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf(MIN_DURATION_MS.toString())

        val result = ArrayList<Song>()
        context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val fileName = c.getString(nameCol).orEmpty().substringBeforeLast('.')
                result += Song(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    title = c.getString(titleCol).cleanOr(fileName.ifBlank { "Tanpa judul" }),
                    artist = c.getString(artistCol).cleanOr("Artis tidak dikenal"),
                    album = c.getString(albumCol).cleanOr("Album tidak dikenal"),
                    albumId = c.getLong(albumIdCol),
                    durationMs = c.getLong(durCol),
                    folder = c.getString(pathCol).orEmpty(),
                    track = if (c.isNull(trackCol)) 0 else c.getInt(trackCol),
                )
            }
        }
        return result.sortedBy { it.title.lowercase() }
    }

    private fun String?.cleanOr(fallback: String): String =
        if (this.isNullOrBlank() || this == "<unknown>") fallback else this.trim()
}
