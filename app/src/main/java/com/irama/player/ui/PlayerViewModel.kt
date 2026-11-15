package com.irama.player.ui

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.irama.player.PlaybackService
import com.irama.player.data.AlbumGroup
import com.irama.player.data.LibraryEvents
import com.irama.player.data.MediaOps
import com.irama.player.data.MusicRepository
import com.irama.player.data.NamedGroup
import com.irama.player.data.PlaylistStore
import com.irama.player.data.Song
import com.irama.player.data.groupAlbums
import com.irama.player.data.groupArtists
import com.irama.player.data.groupFolders
import com.irama.player.data.toMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

data class PlaybackState(
    val hasMedia: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    /** Geser nada dalam semitone (0 = normal). */
    val pitchSemitones: Float = 0f,
) {
    val artUri: Uri? get() = mediaId?.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
}

enum class SortOrder(val label: String) {
    TITLE("Judul"),
    ARTIST("Artis"),
    NEWEST("Terbaru"),
    LONGEST("Terpanjang"),
}

data class PlaylistView(
    val id: String,
    val name: String,
    val songs: List<Song>,
    val isFavorites: Boolean,
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("library_ui", Context.MODE_PRIVATE)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _loading = MutableStateFlow(true) // true sampai pemindaian pertama selesai
    private var loadJob: Job? = null
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _ui = MutableStateFlow(PlaybackState())
    val ui: StateFlow<PlaybackState> = _ui.asStateFlow()

    private val _sort = MutableStateFlow(
        SortOrder.entries.getOrElse(prefs.getInt("sort", 0)) { SortOrder.TITLE }
    )
    val sort: StateFlow<SortOrder> = _sort.asStateFlow()

    val sortedSongs: StateFlow<List<Song>> = combine(_songs, _sort) { list, order ->
        when (order) {
            SortOrder.TITLE -> list
            SortOrder.ARTIST -> list.sortedWith(compareBy<Song>({ it.artist.lowercase() }, { it.title.lowercase() }))
            SortOrder.NEWEST -> list.sortedByDescending { it.dateAdded }
            SortOrder.LONGEST -> list.sortedByDescending { it.durationMs }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<AlbumGroup>> = _songs
        .map { groupAlbums(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<NamedGroup>> = _songs
        .map { groupArtists(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folders: StateFlow<List<NamedGroup>> = _songs
        .map { groupFolders(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val playlists: StateFlow<List<PlaylistView>> = combine(PlaylistStore.playlists, _songs) { lists, songs ->
        val byId = songs.associateBy { it.id }
        lists.map { p ->
            PlaylistView(p.id, p.name, p.songIds.mapNotNull { byId[it] }, p.id == PlaylistStore.FAVORITES)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteIds: StateFlow<Set<Long>> = PlaylistStore.playlists
        .map { lists -> lists.firstOrNull { it.id == PlaylistStore.FAVORITES }?.songIds?.toSet() ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Pesan singkat untuk ditampilkan sebagai Toast. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _permissionRequests = MutableSharedFlow<Pair<MediaOps.Op, IntentSender>>(extraBufferCapacity = 2)
    /** Android 10: operasi butuh izin per file. */
    val permissionRequests: SharedFlow<Pair<MediaOps.Op, IntentSender>> = _permissionRequests.asSharedFlow()

    private var songsById: Map<String, Song> = emptyMap()
    private var controller: MediaController? = null
    private val controllerFuture: ListenableFuture<MediaController>
    private var loadedOnce = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh()
        }
    }

    init {
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(ctx, token).buildAsync()
        controllerFuture.addListener({
            val c = try {
                controllerFuture.get()
            } catch (e: Exception) {
                null
            }
            if (c != null) {
                controller = c
                c.addListener(listener)
                refresh()
                pendingExternal?.let {
                    pendingExternal = null
                    openExternal(it)
                }
            }
        }, ContextCompat.getMainExecutor(ctx))

        // Lagu baru (download / rekaman) atau perubahan file → muat ulang daftar
        viewModelScope.launch {
            LibraryEvents.changed.collect {
                delay(1200) // beri waktu pemindai media Android
                reloadLibrary()
            }
        }

        // Update posisi lagu untuk progress bar
        viewModelScope.launch {
            while (true) {
                val c = controller
                if (c != null && c.isPlaying) {
                    val pos = c.currentPosition
                    val dur = c.duration
                    _ui.update { it.copy(positionMs = pos, durationMs = if (dur > 0) dur else it.durationMs) }
                }
                delay(500)
            }
        }
    }

    // ---------- Library ----------

    fun ensureLoaded() {
        if (!loadedOnce) loadLibrary()
    }

    fun reloadLibrary() {
        ArtCache.clear()
        loadLibrary()
    }

    private fun loadLibrary() {
        if (loadJob?.isActive == true) return
        loadedOnce = true
        loadJob = viewModelScope.launch {
            _loading.value = true
            val list = withContext(Dispatchers.IO) {
                try {
                    MusicRepository.loadSongs(getApplication<Application>())
                } catch (e: Exception) {
                    emptyList()
                }
            }
            songsById = list.associateBy { it.mediaId }
            _songs.value = list
            _loading.value = false
            refresh()
        }
    }

    fun setSort(order: SortOrder) {
        _sort.value = order
        prefs.edit { putInt("sort", order.ordinal) }
    }

    fun songByMediaId(mediaId: String?): Song? = mediaId?.let { songsById[it] }

    private fun toast(msg: String) {
        _messages.tryEmit(msg)
    }

    // ---------- Kontrol pemutar ----------

    fun play(list: List<Song>, index: Int, shuffle: Boolean = false) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setMediaItems(list.map { it.toMediaItem() }, index.coerceIn(0, list.lastIndex), 0L)
        c.shuffleModeEnabled = shuffle
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            when (c.playbackState) {
                Player.STATE_IDLE -> c.prepare()
                Player.STATE_ENDED -> c.seekToDefaultPosition(0)
            }
            c.play()
        }
    }

    fun pause() {
        controller?.pause()
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        val c = controller ?: return
        c.seekTo(positionMs)
        _ui.update { it.copy(positionMs = positionMs) }
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(song), 0)
            return
        }
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
        toast("Diputar setelah lagu ini")
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(song), 0)
            return
        }
        c.addMediaItem(song.toMediaItem())
        toast("Ditambahkan ke antrean")
    }

    /** Kecepatan 0.5–2x dan geser nada dalam semitone (seperti ganti kunci saat karaoke). */
    fun setSpeedPitch(speed: Float, semitones: Float) {
        val c = controller ?: return
        c.playbackParameters = PlaybackParameters(speed, 2f.pow(semitones / 12f))
    }

    private fun removeFromQueue(mediaIds: Set<String>) {
        val c = controller ?: return
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId in mediaIds) c.removeMediaItem(i)
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val song = item?.mediaId?.let { songsById[it] }
        val md = item?.mediaMetadata
        val dur = c.duration
        val params = c.playbackParameters
        _ui.value = PlaybackState(
            hasMedia = item != null,
            mediaId = item?.mediaId,
            title = song?.title ?: md?.title?.toString().orEmpty(),
            artist = song?.artist ?: md?.artist?.toString().orEmpty(),
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = if (dur > 0) dur else song?.durationMs ?: 0L,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            speed = params.speed,
            pitchSemitones = ((12.0 * ln(params.pitch.toDouble()) / ln(2.0)) * 2).roundToInt() / 2f,
        )
    }

    // ---------- Buka file dari aplikasi lain ("Buka dengan → Irama") ----------

    private var pendingExternal: Uri? = null

    private val _openPlayer = MutableStateFlow(false)
    /** True kalau layar pemutar perlu dibuka (setelah membuka file dari luar). */
    val openPlayer: StateFlow<Boolean> = _openPlayer.asStateFlow()

    fun consumeOpenPlayer() {
        _openPlayer.value = false
    }

    fun openExternal(uri: Uri) {
        val c = controller
        if (c == null) {
            pendingExternal = uri // diputar begitu pemutar siap
            return
        }
        viewModelScope.launch {
            // Tunggu daftar lagu selesai dimuat (sebentar saja) supaya bisa dicocokkan
            withTimeoutOrNull(3000) { loadJob?.join() }
            val ctx = getApplication<Application>()
            val info = withContext(Dispatchers.IO) { describeUri(ctx, uri) }
            val library = _songs.value
            val match = library.firstOrNull { it.mediaId == info.mediaStoreId }
                ?: info.displayName?.let { name ->
                    library.firstOrNull { it.displayName == name && (info.size <= 0 || it.sizeBytes == info.size) }
                }

            if (match != null) {
                // Lagu ada di daftar: putar bersama lagu lain di folder yang sama
                val folderSongs = library.filter { it.folder == match.folder }
                play(folderSongs, folderSongs.indexOf(match).coerceAtLeast(0))
            } else {
                val title = info.displayName?.substringBeforeLast('.')?.ifBlank { null } ?: "Audio"
                val item = MediaItem.Builder()
                    .setMediaId(uri.toString())
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist("Dibuka dari aplikasi lain")
                            .build()
                    )
                    .build()
                c.setMediaItems(listOf(item))
                c.shuffleModeEnabled = false
                c.prepare()
                c.play()
            }
            _openPlayer.value = true
        }
    }

    private class UriInfo(val displayName: String?, val size: Long, val mediaStoreId: String?)

    private fun describeUri(ctx: Context, uri: Uri): UriInfo {
        // URI MediaStore (content://media/...): samakan dengan format ID di daftar lagu
        val mediaStoreId = if (uri.authority == MediaStore.AUTHORITY) {
            try {
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ContentUris.parseId(uri)).toString()
            } catch (e: Exception) {
                null
            }
        } else null
        var name: String? = null
        var size = -1L
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val si = c.getColumnIndex(OpenableColumns.SIZE)
                        if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                        if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                    }
                }
        } catch (e: Exception) {
            // sebagian aplikasi tidak memberi info nama file
        }
        if (name == null) name = uri.lastPathSegment?.substringAfterLast('/')
        return UriInfo(name, size, mediaStoreId)
    }

    // ---------- Playlist & Favorit ----------

    fun toggleFavorite(song: Song) {
        val nowFav = PlaylistStore.toggleFavorite(song.id)
        toast(if (nowFav) "♥ Ditambahkan ke Favorit" else "Dihapus dari Favorit")
    }

    fun createPlaylist(name: String, songs: List<Song>): String {
        val id = PlaylistStore.create(name, songs.map { it.id })
        toast("Playlist \"${name.trim()}\" dibuat")
        return id
    }

    fun addToPlaylist(playlistId: String, songs: List<Song>) {
        val name = PlaylistStore.playlists.value.firstOrNull { it.id == playlistId }?.name ?: "playlist"
        val added = PlaylistStore.addSongs(playlistId, songs.map { it.id })
        toast(if (added > 0) "$added lagu ditambahkan ke \"$name\"" else "Lagu sudah ada di \"$name\"")
    }

    fun removeFromPlaylist(playlistId: String, song: Song) {
        PlaylistStore.removeSong(playlistId, song.id)
        toast("Dihapus dari playlist")
    }

    fun renamePlaylist(playlistId: String, name: String) {
        PlaylistStore.rename(playlistId, name)
        toast("Nama playlist diganti")
    }

    fun deletePlaylist(playlistId: String) {
        PlaylistStore.delete(playlistId)
        toast("Playlist dihapus (lagunya tetap ada)")
    }

    // ---------- Hapus / ganti nama / pindah folder ----------

    fun runOp(op: MediaOps.Op, systemDeleted: Boolean) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                MediaOps.perform(getApplication<Application>(), op, systemDeleted)
            }
            when (result) {
                is MediaOps.Result.NeedsPermission -> _permissionRequests.emit(op to result.sender)
                is MediaOps.Result.Done -> {
                    if (op is MediaOps.Delete && result.ok > 0) {
                        removeFromQueue(op.songs.map { it.mediaId }.toSet())
                    }
                    toast(describe(op, result))
                    LibraryEvents.notifyChanged()
                }
            }
        }
    }

    private fun describe(op: MediaOps.Op, r: MediaOps.Result.Done): String = when (op) {
        is MediaOps.Delete ->
            if (r.failed == 0) (if (r.ok == 1) "Lagu dihapus" else "${r.ok} lagu dihapus")
            else "${r.ok} dihapus, ${r.failed} gagal"
        is MediaOps.Rename ->
            if (r.failed == 0) "Nama diganti" else "Judul diganti (nama file tidak bisa diubah)"
        is MediaOps.Move ->
            if (r.failed == 0) "${r.ok} lagu dipindah ke \"${op.label}\""
            else "${r.ok} dipindah, ${r.failed} gagal (folder ini mungkin dilindungi Android)"
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }
}
