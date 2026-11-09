@file:OptIn(ExperimentalMaterial3Api::class)

package com.irama.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.irama.player.audio.AudioFx
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(fx: AudioFx.State, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("Equalizer & Efek") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    Text(
                        if (fx.enabled) "ON" else "OFF",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (fx.enabled) cs.primary else cs.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = fx.enabled, onCheckedChange = { AudioFx.setEnabled(it) })
                    Spacer(Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            SectionTitle("Mode Suara")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (fx.preset == AudioFx.CUSTOM) {
                    item(key = AudioFx.CUSTOM) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text(AudioFx.CUSTOM) },
                            enabled = fx.enabled,
                            colors = chipColors()
                        )
                    }
                }
                items(AudioFx.presets, key = { it.name }) { p ->
                    FilterChip(
                        selected = fx.preset == p.name,
                        onClick = { AudioFx.selectPreset(p) },
                        label = { Text(p.name) },
                        enabled = fx.enabled,
                        colors = chipColors()
                    )
                }
            }

            SectionTitle(if (fx.eqSupported) "Equalizer ${fx.bandFreqsHz.size} Band" else "Equalizer")
            Card(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
                shape = RoundedCornerShape(20.dp)
            ) {
                if (fx.eqSupported) {
                    EqPanel(
                        freqs = fx.bandFreqsHz,
                        levels = fx.bandLevels,
                        minLevel = fx.minLevel,
                        maxLevel = fx.maxLevel,
                        enabled = fx.enabled,
                        onChange = { band, level -> AudioFx.setBand(band, level) },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                    )
                } else {
                    Text(
                        "Equalizer tidak didukung di HP ini.",
                        modifier = Modifier.padding(16.dp),
                        color = cs.onSurfaceVariant
                    )
                }
            }
            Text(
                "Geser titik ke atas/bawah untuk mengatur tiap frekuensi.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            SectionTitle("Efek")
            EffectCard(
                icon = Icons.Rounded.Speaker,
                title = "Bass Boost",
                desc = "Pertebal dentuman bass",
                value = fx.bass,
                supported = fx.bassSupported,
                enabled = fx.enabled,
                onChange = { AudioFx.setBass(it) }
            )
            EffectCard(
                icon = Icons.Rounded.SurroundSound,
                title = "Surround 3D",
                desc = "Suara terasa lebih luas — paling terasa pakai earphone",
                value = fx.surround,
                supported = fx.surroundSupported,
                enabled = fx.enabled,
                onChange = { AudioFx.setSurround(it) }
            )
            EffectCard(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                title = "Penguat Volume",
                desc = "Bikin suara lebih keras (jangan terlalu tinggi, bisa pecah)",
                value = fx.loudness,
                supported = fx.loudnessSupported,
                enabled = fx.enabled,
                onChange = { AudioFx.setLoudness(it) }
            )

            OutlinedButton(
                onClick = { AudioFx.reset() },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reset ke Normal")
            }
            Text(
                "Tips: kalau Dolby Atmos di HP Samsung kamu aktif, efeknya ikut bertumpuk. " +
                    "Kalau suara terasa terlalu berat, matikan salah satunya.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp)
    )
}

@Composable
private fun EffectCard(
    icon: ImageVector,
    title: String,
    desc: String,
    value: Int,
    supported: Boolean,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val active = enabled && supported
    Card(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(cs.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = cs.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (supported) desc else "Tidak didukung di HP ini",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${value / 10}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (value > 0 && active) cs.primary else cs.onSurfaceVariant
                )
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = 0f..1000f,
                enabled = active
            )
        }
    }
}

/** Panel equalizer: kurva + titik yang bisa digeser per band. */
@Composable
private fun EqPanel(
    freqs: List<Int>,
    levels: List<Int>,
    minLevel: Int,
    maxLevel: Int,
    enabled: Boolean,
    onChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val n = levels.size
    if (n == 0) return
    val cs = MaterialTheme.colorScheme
    val accent = if (enabled) cs.primary else cs.onSurfaceVariant
    val track = cs.onSurface.copy(alpha = 0.10f)
    val zeroLine = cs.onSurface.copy(alpha = 0.20f)
    val thumbCenter = cs.surfaceContainer
    val range = (maxLevel - minLevel).coerceAtLeast(1)
    val latestOnChange by rememberUpdatedState(onChange)

    Column(modifier.alpha(if (enabled) 1f else 0.5f)) {
        Row(Modifier.fillMaxWidth()) {
            levels.forEach { lv ->
                Text(
                    formatDb(lv),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (lv != 0 && enabled) cs.primary else cs.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(n, minLevel, maxLevel, enabled) {
                    if (!enabled) return@pointerInput
                    val pad = 14.dp.toPx()
                    fun levelAt(y: Float): Int {
                        val h = size.height - 2 * pad
                        val frac = 1f - ((y - pad) / h).coerceIn(0f, 1f)
                        val raw = minLevel + frac * range
                        return ((raw / 50f).roundToInt() * 50).coerceIn(minLevel, maxLevel)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val band = ((down.position.x / size.width) * n).toInt().coerceIn(0, n - 1)
                        latestOnChange(band, levelAt(down.position.y))
                        down.consume()
                        drag(down.id) { change ->
                            latestOnChange(band, levelAt(change.position.y))
                            change.consume()
                        }
                    }
                }
        ) {
            val pad = 14.dp.toPx()
            val h = size.height - 2 * pad
            val colW = size.width / n
            fun yOf(level: Int): Float = pad + (1f - (level - minLevel).toFloat() / range) * h
            val zeroY = yOf(0.coerceIn(minLevel, maxLevel))

            drawLine(zeroLine, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())

            val pts = levels.mapIndexed { i, lv -> Offset(colW * (i + 0.5f), yOf(lv)) }
            pts.forEach { p ->
                drawLine(
                    track,
                    Offset(p.x, pad),
                    Offset(p.x, pad + h),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            val fill = curvePath(pts).apply {
                lineTo(pts.last().x, pad + h)
                lineTo(pts.first().x, pad + h)
                close()
            }
            drawPath(
                fill,
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                    startY = pad,
                    endY = pad + h
                )
            )
            drawPath(curvePath(pts), accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

            pts.forEach { p ->
                drawLine(accent, Offset(p.x, zeroY), p, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(accent, radius = 10.dp.toPx(), center = p)
                drawCircle(thumbCenter, radius = 4.5.dp.toPx(), center = p)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            freqs.forEach { f ->
                Text(
                    formatHz(f),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}

private fun curvePath(pts: List<Offset>): Path = Path().apply {
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val p0 = pts[i - 1]
        val p1 = pts[i]
        val mx = (p0.x + p1.x) / 2f
        cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
    }
}

private fun formatDb(millibel: Int): String =
    if (millibel == 0) "0" else String.format(Locale.US, "%+.1f", millibel / 100f)

private fun formatHz(hz: Int): String =
    if (hz >= 1000) {
        val k = hz / 1000f
        if (k == k.roundToInt().toFloat()) "${k.roundToInt()}k" else String.format(Locale.US, "%.1fk", k)
    } else {
        "$hz"
    }
