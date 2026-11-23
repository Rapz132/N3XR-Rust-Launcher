package com.movtery.zalithlauncher.ui.screens.content

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.chat.ChatManager
import com.movtery.zalithlauncher.chat.ChatMedia
import com.movtery.zalithlauncher.chat.ChatMessage
import com.movtery.zalithlauncher.chat.ChatMessageType
import com.movtery.zalithlauncher.chat.ChatState
import com.movtery.zalithlauncher.chat.VoicePlayer
import com.movtery.zalithlauncher.chat.VoiceRecorder
import com.movtery.zalithlauncher.game.account.AccountsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val OnlineGreen = Color(0xFF3DDC84)
private val RecordRed = Color(0xFFE5484D)

@Composable
fun ChatDialog(onDismiss: () -> Unit) {
    val roomCode by ChatManager.roomCode.collectAsStateWithLifecycle()
    val account by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()
    val playerName = account?.username ?: "Pemain"

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
            if (roomCode == null) {
                JoinContent(playerName = playerName, onClose = onDismiss)
            } else {
                RoomContent(code = roomCode.orEmpty(), onClose = onDismiss)
            }
        }
    }
}

/* ---------------------------------------------------------------- */
/*  Layar masuk / buat grup                                          */
/* ---------------------------------------------------------------- */

@Composable
private fun JoinContent(playerName: String, onClose: () -> Unit) {
    val error by ChatManager.error.collectAsStateWithLifecycle()
    var codeInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name = playerName, size = 40)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = "Chat grup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Masuk sebagai $playerName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CloseButton(onClose)
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Buat grup baru",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Dapatkan kode grup lalu bagikan ke temanmu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { ChatManager.createRoom(playerName) }
                    ) {
                        Text("Buat grup")
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Gabung grup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = codeInput,
                        onValueChange = { value ->
                            codeInput = value.uppercase()
                                .filter { it.isLetterOrDigit() }
                                .take(ChatManager.CODE_LENGTH)
                        },
                        label = { Text("Kode grup") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (codeInput.length == ChatManager.CODE_LENGTH) {
                                    ChatManager.joinRoom(codeInput, playerName)
                                }
                            }
                        )
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = codeInput.length == ChatManager.CODE_LENGTH,
                        onClick = { ChatManager.joinRoom(codeInput, playerName) }
                    ) {
                        Text("Gabung")
                    }
                }
            }
        }

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text(
            text = "Pesan, foto, dan suara tidak disimpan dan hilang saat kamu keluar.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ---------------------------------------------------------------- */
/*  Layar ruang chat                                                 */
/* ---------------------------------------------------------------- */

@Composable
private fun RoomContent(code: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val state by ChatManager.state.collectAsStateWithLifecycle()
    val messages by ChatManager.messages.collectAsStateWithLifecycle()
    val error by ChatManager.error.collectAsStateWithLifecycle()
    val lastSentAt by ChatManager.lastSentAt.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var input by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var previewMedia by remember { mutableStateOf<String?>(null) }
    var recordingSince by remember { mutableLongStateOf(0L) }
    var playingId by remember { mutableStateOf<String?>(null) }

    val recorder = remember { VoiceRecorder(context) }
    val player = remember { VoicePlayer(context) { playingId = it } }
    DisposableEffect(Unit) {
        onDispose {
            recorder.cancel()
            player.release()
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val remainingSec = ((ChatManager.COOLDOWN_MS - (now - lastSentAt) + 999) / 1000).coerceAtLeast(0)

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2500)
            notice = null
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val connected = state == ChatState.Connected
    val recording = recordingSince > 0L
    val canSend = connected && remainingSec == 0L && input.isNotBlank()

    val send: () -> Unit = {
        if (canSend && ChatManager.sendMessage(input)) input = ""
    }

    // --- Pesan suara ---
    val finishRecording: (Boolean) -> Unit = { shouldSend ->
        recordingSince = 0L
        if (shouldSend) {
            val result = recorder.stop()
            if (result == null) {
                notice = "Rekaman terlalu pendek."
            } else if (!ChatManager.sendAudio(result.base64, result.durationMs)) {
                notice = "Tunggu sebentar sebelum mengirim lagi."
            }
        } else {
            recorder.cancel()
        }
    }
    val startRecording: () -> Unit = {
        player.stop()
        if (recorder.start()) {
            recordingSince = System.currentTimeMillis()
        } else {
            notice = "Gagal mulai merekam."
        }
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording() else notice = "Izin mikrofon ditolak."
    }
    val onMicClick: () -> Unit = {
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startRecording() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(now) {
        if (recordingSince > 0L && now - recordingSince >= ChatManager.MAX_AUDIO_MS) {
            finishRecording(true)
        }
    }

    // --- Foto ---
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val base64 = withContext(Dispatchers.IO) { ChatMedia.encodeImage(context, uri) }
                if (base64 == null) {
                    notice = "Gagal memproses foto."
                } else if (!ChatManager.sendImage(base64)) {
                    notice = "Tunggu sebentar sebelum mengirim lagi."
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chat grup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (state) {
                                    ChatState.Connected -> OnlineGreen
                                    ChatState.Connecting -> MaterialTheme.colorScheme.tertiary
                                    ChatState.Error -> MaterialTheme.colorScheme.error
                                    ChatState.Idle -> MaterialTheme.colorScheme.outline
                                }
                            )
                    )
                    Text(
                        modifier = Modifier.padding(start = 6.dp),
                        text = when (state) {
                            ChatState.Connecting -> "Menyambung..."
                            ChatState.Connected -> "Tersambung"
                            ChatState.Error -> error ?: "Terputus"
                            ChatState.Idle -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (state == ChatState.Error) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (copied) "Tersalin ✓" else "Salin",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            OutlinedButton(onClick = { ChatManager.leave() }) { Text("Keluar") }
            CloseButton(onClose)
        }

        // Daftar pesan
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = "Belum ada pesan.\nBagikan kode grup ke temanmu!",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageItem(
                        message = message,
                        playing = playingId == message.id,
                        onPlay = { message.media?.let { player.toggle(message.id, it) } },
                        onOpenImage = { previewMedia = it }
                    )
                }
            }
        }

        notice?.let {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Area input
        if (recording) {
            val elapsed = (now - recordingSince).coerceAtLeast(0L)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(RecordRed)
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = "Merekam ${formatDuration(elapsed)} / ${formatDuration(ChatManager.MAX_AUDIO_MS)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = { finishRecording(false) }) { Text("Batal") }
                Button(onClick = { finishRecording(true) }) { Text("Kirim") }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EmojiButton("📷", enabled = connected) { imageLauncher.launch("image/*") }
                EmojiButton("🎤", enabled = connected, onClick = onMicClick)

                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = input,
                    onValueChange = { input = it.take(300) },
                    placeholder = { Text("Tulis pesan...") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    trailingIcon = {
                        if (input.length > 240) {
                            Text(
                                modifier = Modifier.padding(end = 12.dp),
                                text = "${input.length}/300",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.size(6.dp))
                Button(
                    modifier = Modifier.height(52.dp),
                    enabled = canSend,
                    onClick = send
                ) {
                    Text(if (remainingSec > 0) "Kirim ($remainingSec)" else "Kirim")
                }
            }
        }
    }

    previewMedia?.let { media ->
        ImagePreview(media = media, onDismiss = { previewMedia = null })
    }
}

/* ---------------------------------------------------------------- */
/*  Komponen kecil                                                   */
/* ---------------------------------------------------------------- */

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            modifier = Modifier.size(22.dp),
            painter = painterResource(R.drawable.ic_close),
            contentDescription = "Tutup"
        )
    }
}

@Composable
private fun EmojiButton(emoji: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Text(
            modifier = Modifier.alpha(if (enabled) 1f else 0.4f),
            text = emoji,
            fontSize = 22.sp
        )
    }
}

@Composable
private fun Avatar(name: String, size: Int = 32) {
    val hue = remember(name) { (name.hashCode().toLong().and(0x7fffffffL) % 360L).toFloat() }
    val background = remember(hue) { Color.hsv(hue, 0.45f, 0.65f) }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun ImagePreview(media: String, onDismiss: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, media) {
        value = withContext(Dispatchers.IO) { ChatMedia.decodeImage(media)?.asImageBitmap() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            bitmap?.let {
                Image(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun MessageItem(
    message: ChatMessage,
    playing: Boolean,
    onPlay: () -> Unit,
    onOpenImage: (String) -> Unit
) {
    if (message.isSystem) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    text = message.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val mine = message.senderId == ChatManager.clientId

    val bubbleShape = if (mine) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!mine) {
            Avatar(name = message.sender)
            Spacer(modifier = Modifier.size(8.dp))
        }

        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (!mine) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (message.isAdmin) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                text = "Admin",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.widthIn(max = 380.dp),
                shape = bubbleShape,
                color = if (mine) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (mine) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            ) {
                when (message.type) {
                    ChatMessageType.Text -> Text(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    ChatMessageType.Image -> ImageBubble(
                        media = message.media,
                        onClick = { message.media?.let(onOpenImage) }
                    )

                    ChatMessageType.Audio -> AudioBubble(
                        playing = playing,
                        durationMs = message.durationMs,
                        onPlay = onPlay
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageBubble(media: String?, onClick: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, media) {
        value = withContext(Dispatchers.IO) { media?.let { ChatMedia.decodeImage(it)?.asImageBitmap() } }
    }
    val shape = RoundedCornerShape(14.dp)
    val loaded = bitmap
    Box(modifier = Modifier.padding(4.dp)) {
        if (loaded == null) {
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 110.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Memuat foto...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Image(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .heightIn(max = 220.dp)
                    .clip(shape)
                    .clickable(onClick = onClick),
                bitmap = loaded,
                contentDescription = "Foto",
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun AudioBubble(playing: Boolean, durationMs: Long, onPlay: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            if (playing) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(3.dp))
                )
            } else {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(R.drawable.ic_play_arrow_filled),
                    contentDescription = "Putar",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Column {
            Text(
                text = if (playing) "Memutar..." else "Pesan suara",
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
