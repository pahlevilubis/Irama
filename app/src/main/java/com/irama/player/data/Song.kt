package com.irama.player.data

import android.content.ContentUris
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val folder: String,
    val track: Int,
    val displayName: String = "",
    val sizeBytes: Long = 0L,
    val mime: String = "",
    /** Waktu file ditambahkan (detik sejak 1970). */
    val dateAdded: Long = 0L,
) {
    /** ID yang dipakai pemutar (sama dengan URI lagu). */
    val mediaId: String = uri.toString()

    /** Ekstensi file termasuk titik, misalnya ".mp3". */
    val extension: String
        get() = displayName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
}

data class AlbumGroup(
    val id: Long,
    val title: String,
    val artist: String,
    val songs: List<Song>,
)

data class NamedGroup(
    val key: String,
    val name: String,
    val subtitle: String,
    val songs: List<Song>,
)

private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")

fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(ContentUris.withAppendedId(ALBUM_ART_URI, albumId))
                .build()
        )
        .build()

fun groupAlbums(songs: List<Song>): List<AlbumGroup> =
    songs.groupBy { it.albumId }
        .map { (id, list) ->
            val sorted = list.sortedWith(compareBy<Song>({ it.track % 1000 }, { it.title.lowercase() }))
            val artists = sorted.map { it.artist }.distinct()
            AlbumGroup(
                id = id,
                title = sorted.first().album,
                artist = if (artists.size == 1) artists.first() else "Berbagai Artis",
                songs = sorted,
            )
        }
        .sortedBy { it.title.lowercase() }

fun groupArtists(songs: List<Song>): List<NamedGroup> =
    songs.groupBy { it.artist }
        .map { (name, list) ->
            NamedGroup(
                key = name,
                name = name,
                subtitle = "${list.size} lagu",
                songs = list.sortedBy { it.title.lowercase() },
            )
        }
        .sortedBy { it.name.lowercase() }

fun groupFolders(songs: List<Song>): List<NamedGroup> =
    songs.groupBy { it.folder }
        .map { (path, list) ->
            val name = path.trimEnd('/').substringAfterLast('/').ifBlank { "Penyimpanan" }
            NamedGroup(
                key = path,
                name = name,
                subtitle = "${list.size} lagu  •  ${path.ifBlank { "/" }}",
                songs = list.sortedBy { it.title.lowercase() },
            )
        }
        .sortedBy { it.name.lowercase() }
