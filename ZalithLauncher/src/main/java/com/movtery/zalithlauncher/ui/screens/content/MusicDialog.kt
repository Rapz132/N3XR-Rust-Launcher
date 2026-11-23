package com.movtery.zalithlauncher.ui.screens.content

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.music.MusicManager
import com.movtery.zalithlauncher.music.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MusicDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    remember { MusicManager.init(context) }
    var tab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = "Musik",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TabButton("Lokal", tab == 0) { tab = 0 }
                    TabButton("Audius", tab == 1) { tab = 1 }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            modifier = Modifier.size(22.dp),
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Tutup"
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    if (tab == 0) LocalTab() else AudiusTab()
                }

                PlayerBar()
            }
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

/* ---------------------------- Tab Lokal ---------------------------- */

@Composable
private fun LocalTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library by MusicManager.library.collectAsStateWithLifecycle()
    val current by MusicManager.current.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val count = withContext(Dispatchers.IO) { MusicManager.addLocal(context, uris) }
                notice = if (count == uris.size) "$count lagu ditambahkan"
                else "$count dari ${uris.size} lagu berhasil ditambahkan"
            }
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3000)
            notice = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { picker.launch(arrayOf("audio/*")) }) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Tambah lagu")
            }
            notice?.let {
                Text(text = it, style = MaterialTheme.typography.labelMedium)
            }
        }

        if (library.isEmpty()) {
            Text(
                text = "Belum ada lagu. Tap \"Tambah lagu\" untuk memilih file audio dari HP-mu. " +
                    "Filenya disalin ke penyimpanan launcher dan tidak diunggah ke mana pun.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(library, key = { _, track -> track.id }) { index, track ->
                TrackRow(
                    track = track,
                    selected = current?.id == track.id,
                    onClick = { MusicManager.play(library, index) },
                    trailing = {
                        IconButton(onClick = { MusicManager.removeLocal(track) }) {
                            Icon(
                                modifier = Modifier.size(20.dp),
                                painter = painterResource(R.drawable.ic_n3x_delete),
                                contentDescription = "Hapus"
                            )
                        }
                    }
                )
            }
        }
    }
}

/* ---------------------------- Tab Audius --------------------------- */

@Composable
private fun AudiusTab() {
    val results by MusicManager.results.collectAsStateWithLifecycle()
    val searching by MusicManager.searching.collectAsStateWithLifecycle()
    val searchError by MusicManager.searchError.collectAsStateWithLifecycle()
    val current by MusicManager.current.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = query,
                onValueChange = { query = it.take(80) },
                placeholder = { Text("Cari lagu atau artis di Audius") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { MusicManager.search(query) })
            )
            Button(
                enabled = query.isNotBlank() && !searching,
                onClick = { MusicManager.search(query) }
            ) {
                Text(if (searching) "Mencari..." else "Cari")
            }
        }

        searchError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Text(
            text = "Musik dari Audius. Hargai karya para artisnya.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(results, key = { _, track -> track.id }) { index, track ->
                TrackRow(
                    track = track,
                    selected = current?.id == track.id,
                    onClick = { MusicManager.play(results, index) },
                    trailing = null
                )
            }
        }
    }
}

/* ----------------------------- Komponen ---------------------------- */

@Composable
private fun TrackRow(
    track: MusicTrack,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (track.durationMs > 0) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = formatTime(track.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun PlayerBar() {
    val current by MusicManager.current.collectAsStateWithLifecycle()
    val playing by MusicManager.isPlaying.collectAsStateWithLifecycle()
    val shuffle by MusicManager.shuffle.collectAsStateWithLifecycle()
    val error by MusicManager.error.collectAsStateWithLifecycle()

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            if (!dragging) position = MusicManager.position()
            duration = MusicManager.duration()
            delay(500)
        }
    }

    val hasTrack = current != null
    val shownPosition = if (dragging) dragValue.toLong() else position

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = current?.title ?: "Belum ada lagu diputar",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = error ?: current?.artist.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(enabled = hasTrack, onClick = { MusicManager.previous() }) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(R.drawable.ic_n3x_skip_previous),
                    contentDescription = "Sebelumnya"
                )
            }
            IconButton(enabled = hasTrack, onClick = { MusicManager.toggle() }) {
                Icon(
                    modifier = Modifier.size(30.dp),
                    painter = painterResource(
                        if (playing) R.drawable.ic_n3x_pause else R.drawable.ic_play_arrow_filled
                    ),
                    contentDescription = if (playing) "Jeda" else "Putar"
                )
            }
            IconButton(enabled = hasTrack, onClick = { MusicManager.next() }) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(R.drawable.ic_n3x_skip_next),
                    contentDescription = "Berikutnya"
                )
            }
            if (shuffle) {
                Button(onClick = { MusicManager.toggleShuffle() }) { Text("Acak") }
            } else {
                OutlinedButton(onClick = { MusicManager.toggleShuffle() }) { Text("Acak") }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = formatTime(shownPosition), style = MaterialTheme.typography.labelSmall)
            Slider(
                modifier = Modifier.weight(1f),
                enabled = hasTrack && duration > 0,
                value = shownPosition.toFloat().coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    MusicManager.seekTo(dragValue.toLong())
                    position = dragValue.toLong()
                    dragging = false
                }
            )
            Text(text = formatTime(duration), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
