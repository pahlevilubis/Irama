package com.irama.player.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Sinyal "isi perpustakaan berubah" (lagu baru didownload/direkam, dihapus, dipindah). */
object LibraryEvents {
    private val _changed = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val changed: SharedFlow<Unit> = _changed.asSharedFlow()

    fun notifyChanged() {
        _changed.tryEmit(Unit)
    }
}

/** Judul pengganti hasil "Ganti nama" — dipakai kalau tag di dalam file berisi judul lama. */
object TitleOverrides {
    private const val PREFS = "song_titles"

    fun all(context: Context): Map<Long, String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) -> k.toLongOrNull()?.let { id -> (v as? String)?.let { id to it } } }
            .toMap()

    fun set(context: Context, songId: Long, title: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(songId.toString(), title) }
    }

    fun remove(context: Context, songIds: Collection<Long>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            songIds.forEach { id -> this.remove(id.toString()) }
        }
    }
}

data class Playlist(val id: String, val name: String, val songIds: List<Long>)

/** Playlist & Favorit, disimpan sebagai JSON di penyimpanan internal aplikasi. */
object PlaylistStore {
    const val FAVORITES = "fav"
    const val FAVORITES_NAME = "Favorit"

    private val _playlists = MutableStateFlow<List<Playlist>>(listOf(Playlist(FAVORITES, FAVORITES_NAME, emptyList())))
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private var file: File? = null

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, "playlists.json")
        file = f
        val loaded = try {
            if (f.exists()) parse(f.readText()) else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val fav = loaded.firstOrNull { it.id == FAVORITES } ?: Playlist(FAVORITES, FAVORITES_NAME, emptyList())
        _playlists.value = listOf(fav.copy(name = FAVORITES_NAME)) + loaded.filter { it.id != FAVORITES }
    }

    fun create(name: String, songIds: List<Long> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        update { it + Playlist(id, name.trim(), songIds.distinct()) }
        return id
    }

    fun rename(id: String, name: String) {
        if (id == FAVORITES) return
        update { list -> list.map { if (it.id == id) it.copy(name = name.trim()) else it } }
    }

    fun delete(id: String) {
        if (id == FAVORITES) return
        update { list -> list.filter { it.id != id } }
    }

    /** Tambah lagu ke playlist; mengembalikan jumlah lagu yang benar-benar baru ditambahkan. */
    fun addSongs(id: String, songIds: List<Long>): Int {
        var added = 0
        update { list ->
            list.map { p ->
                if (p.id != id) p else {
                    val existing = p.songIds.toHashSet()
                    val fresh = songIds.distinct().filter { it !in existing }
                    added = fresh.size
                    p.copy(songIds = p.songIds + fresh)
                }
            }
        }
        return added
    }

    fun removeSong(id: String, songId: Long) {
        update { list -> list.map { if (it.id == id) it.copy(songIds = it.songIds - songId) else it } }
    }

    fun isFavorite(songId: Long): Boolean =
        _playlists.value.firstOrNull { it.id == FAVORITES }?.songIds?.contains(songId) == true

    /** Mengembalikan true kalau sekarang menjadi favorit. */
    fun toggleFavorite(songId: Long): Boolean {
        val nowFav = !isFavorite(songId)
        update { list ->
            list.map { p ->
                if (p.id != FAVORITES) p
                else if (nowFav) p.copy(songIds = listOf(songId) + p.songIds)
                else p.copy(songIds = p.songIds - songId)
            }
        }
        return nowFav
    }

    /** Buang ID lagu yang sudah dihapus dari semua playlist. */
    fun forget(songIds: Collection<Long>) {
        val gone = songIds.toHashSet()
        update { list -> list.map { p -> p.copy(songIds = p.songIds.filter { it !in gone }) } }
    }

    private fun update(change: (List<Playlist>) -> List<Playlist>) {
        val new = change(_playlists.value)
        if (new == _playlists.value) return
        _playlists.value = new
        save(new)
    }

    private fun save(list: List<Playlist>) {
        val f = file ?: return
        try {
            val arr = JSONArray()
            list.forEach { p ->
                arr.put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("songs", JSONArray().apply { p.songIds.forEach { put(it) } })
                })
            }
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(JSONObject().put("playlists", arr).toString())
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (e: Exception) {
            // penyimpanan gagal: biarkan, data tetap ada di memori
        }
    }

    private fun parse(text: String): List<Playlist> {
        val arr = JSONObject(text).optJSONArray("playlists") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val songs = o.optJSONArray("songs")
            Playlist(
                id = o.optString("id").ifBlank { return@mapNotNull null },
                name = o.optString("name").ifBlank { "Playlist" },
                songIds = if (songs == null) emptyList() else (0 until songs.length()).map { songs.getLong(it) },
            )
        }
    }
}
