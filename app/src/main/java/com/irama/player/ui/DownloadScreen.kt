@file:OptIn(ExperimentalMaterial3Api::class)

package com.irama.player.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.irama.player.download.YtDownloader

@Composable
fun DownloadScreen(onBack: () -> Unit, miniPlayer: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val jobs by YtDownloader.jobs.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val errorText = error

    fun submit() {
        if (YtDownloader.enqueue(link)) {
            link = ""
            error = null
            keyboard?.hide()
        } else {
            error = "Itu bukan link YouTube. Contoh: https://youtu.be/abc123"
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("Download dari YouTube") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        bottomBar = miniPlayer,
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item(key = "input") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = link,
                            onValueChange = {
                                link = it
                                error = null
                            },
                            label = { Text("Link YouTube") },
                            placeholder = { Text("https://youtu.be/…") },
                            singleLine = true,
                            isError = errorText != null,
                            supportingText = if (errorText != null) {
                                { Text(errorText) }
                            } else null,
                            trailingIcon = {
                                IconButton(onClick = {
                                    clipboard.getText()?.text?.let {
                                        link = it.trim()
                                        error = null
                                    }
                                }) {
                                    Icon(Icons.Rounded.ContentPaste, contentDescription = "Tempel dari clipboard")
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { submit() },
                            enabled = link.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Download Audio")
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Tip(Icons.Rounded.Share, "Cara cepat: di aplikasi YouTube ketuk Bagikan, lalu pilih Irama.")
                Tip(Icons.Rounded.Folder, "Hasilnya file audio M4A di folder Music/Irama dan langsung muncul di daftar lagu.")
                Spacer(Modifier.height(10.dp))
                if (jobs.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Unduhan",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        if (jobs.any { !it.active }) {
                            TextButton(onClick = { YtDownloader.clearFinished() }) { Text("Bersihkan") }
                        }
                    }
                }
            }
            items(jobs, key = { it.id }) { job -> JobCard(job) }
        }
    }
}

@Composable
private fun Tip(icon: ImageVector, text: String) {
    Row(
        Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun JobCard(job: YtDownloader.Job) {
    val cs = MaterialTheme.colorScheme
    val failed = job.phase == YtDownloader.Phase.FAILED
    val done = job.phase == YtDownloader.Phase.DONE
    Card(
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (failed) cs.errorContainer else cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        done -> Icons.Rounded.CheckCircle
                        failed -> Icons.Rounded.ErrorOutline
                        else -> Icons.Rounded.Download
                    },
                    contentDescription = null,
                    tint = if (failed) cs.error else cs.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    job.title.ifBlank { job.url },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (job.channel.isNotBlank()) {
                    Text(
                        job.channel,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (job.active) {
                    Spacer(Modifier.height(8.dp))
                    if (job.progress >= 0f) {
                        LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    job.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) cs.error else cs.onSurfaceVariant
                )
            }
            if (failed) {
                IconButton(onClick = { YtDownloader.retry(job.id) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Coba lagi")
                }
            }
        }
    }
}
