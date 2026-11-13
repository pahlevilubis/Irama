package com.irama.player.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.irama.player.PlaybackService
import com.irama.player.data.AlbumGroup
import com.irama.player.data.MusicRepository
import com.irama.player.data.NamedGroup
import com.irama.player.data.Song
import com.irama.player.data.groupAlbums
import com.irama.player.data.groupArtists
import com.irama.player.data.groupFolders
import com.irama.player.data.toMediaItem
import com.irama.player.download.YtDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
) {
    val artUri: Uri? get() = mediaId?.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _loading = MutableStateFlow(true) // true sampai pemindaian pertama selesai
    private var loadJob: Job? = null
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _ui = MutableStateFlow(PlaybackState())
    val ui: StateFlow<PlaybackState> = _ui.asStateFlow()

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
            val c = try { controllerFuture.get() } catch (e: Exception) { null }
            if (c != null) {
                controller = c
                c.addListener(listener)
                refresh()
            }
        }, ContextCompat.getMainExecutor(ctx))

        // Lagu hasil download YouTube langsung masuk daftar
        viewModelScope.launch {
            YtDownloader.finished.collect { reloadLibrary() }
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

    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val song = item?.mediaId?.let { songsById[it] }
        val md = item?.mediaMetadata
        val dur = c.duration
        _ui.value = PlaybackState(
            hasMedia = item != null,
            mediaId = item?.mediaId,
            title = md?.title?.toString() ?: song?.title.orEmpty(),
            artist = md?.artist?.toString() ?: song?.artist.orEmpty(),
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = if (dur > 0) dur else song?.durationMs ?: 0L,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
        )
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }
}
