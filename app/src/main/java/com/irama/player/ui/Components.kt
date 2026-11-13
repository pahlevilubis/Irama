package com.irama.player.ui

import android.content.Context
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.irama.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.Locale

// ---------- Cover album ----------

object ArtCache {
    private val cache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet())

    fun peek(uri: Uri, size: Int): ImageBitmap? = cache.get("$uri@$size")

    fun load(context: Context, uri: Uri, size: Int): ImageBitmap? {
        val key = "$uri@$size"
        cache.get(key)?.let { return it }
        if (key in missing) return null
        val bitmap = try {
            context.contentResolver.loadThumbnail(uri, Size(size, size), null)
        } catch (e: Exception) {
            null
        }
        return if (bitmap != null) {
            bitmap.asImageBitmap().also { cache.put(key, it) }
        } else {
            missing.add(key)
            null
        }
    }

    fun clear() {
        cache.evictAll()
        missing.clear()
    }
}

@Composable
fun AlbumArt(
    uri: Uri?,
    modifier: Modifier = Modifier,
    sizePx: Int = 160,
    corner: Dp = 10.dp,
    iconSize: Dp = 22.dp,
) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState(initialValue = uri?.let { ArtCache.peek(it, sizePx) }, uri, sizePx) {
        value = if (uri == null) {
            null
        } else {
            ArtCache.peek(uri, sizePx) ?: withContext(Dispatchers.IO) { ArtCache.load(context, uri, sizePx) }
        }
    }
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(cs.surfaceContainerHighest, cs.surfaceContainerLow))),
        contentAlignment = Alignment.Center
    ) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (iconSize > 0.dp) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

// ---------- Baris lagu ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    isFavorite: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(start = 16.dp, end = if (onMore != null) 4.dp else 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(song.uri, Modifier.size(52.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) cs.primary else cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${song.artist}  •  ${formatTime(song.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isFavorite) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = "Favorit",
                tint = cs.secondary,
                modifier = Modifier.size(16.dp)
            )
        }
        if (isCurrent) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.GraphicEq, contentDescription = "Sedang diputar", tint = cs.primary)
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Menu lagu", tint = cs.onSurfaceVariant)
            }
        }
    }
}

// ---------- Mini player di bawah layar ----------

@Composable
fun MiniPlayer(
    state: PlaybackState,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    if (!state.hasMedia) return
    val cs = MaterialTheme.colorScheme
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else 0f

    Surface(color = cs.surfaceContainerHigh) {
        Column(Modifier.navigationBarsPadding()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(cs.onSurface.copy(alpha = 0.08f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(cs.primary)
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArt(state.artUri, Modifier.size(46.dp), corner = 8.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        state.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Jeda" else "Putar",
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya", modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

// ---------- Lapisan layar dengan animasi geser ----------

private class Holder<T> {
    var value: T? = null
}

/** Tampilkan [content] di atas layar lain dengan animasi geser selama [value] tidak null. */
@Composable
fun <T : Any> SlideLayer(
    value: T?,
    fromBottom: Boolean = false,
    content: @Composable (T) -> Unit,
) {
    val holder = remember { Holder<T>() }
    if (value != null) holder.value = value
    AnimatedVisibility(
        visible = value != null,
        enter = if (fromBottom) {
            slideInVertically(tween(320)) { it } + fadeIn(tween(200))
        } else {
            slideInHorizontally(tween(280)) { it } + fadeIn(tween(200))
        },
        exit = if (fromBottom) {
            slideOutVertically(tween(280)) { it } + fadeOut(tween(220))
        } else {
            slideOutHorizontally(tween(250)) { it } + fadeOut(tween(220))
        },
    ) {
        holder.value?.let { content(it) }
    }
}

// ---------- Format ----------

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
