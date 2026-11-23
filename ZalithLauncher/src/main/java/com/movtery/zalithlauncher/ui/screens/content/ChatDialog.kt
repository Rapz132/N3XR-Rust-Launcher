package com.movtery.zalithlauncher.ui.screens.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.chat.ChatManager
import com.movtery.zalithlauncher.chat.ChatMessage
import com.movtery.zalithlauncher.chat.ChatState
import com.movtery.zalithlauncher.game.account.AccountsManager
import kotlinx.coroutines.delay

private val OnlineGreen = Color(0xFF3DDC84)

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
            // Buat grup
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

            // Gabung grup
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
            text = "Pesan tidak disimpan dan hilang saat kamu keluar.",
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
    val state by ChatManager.state.collectAsStateWithLifecycle()
    val messages by ChatManager.messages.collectAsStateWithLifecycle()
    val error by ChatManager.error.collectAsStateWithLifecycle()
    val lastSentAt by ChatManager.lastSentAt.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var input by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

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

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val canSend = state == ChatState.Connected && remainingSec == 0L && input.isNotBlank()
    val send: () -> Unit = {
        if (canSend && ChatManager.sendMessage(input)) input = ""
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

            // Chip kode grup (klik untuk salin)
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 20.dp,
                    vertical = 10.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageItem(message)
                }
            }
        }

        // Kolom input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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

/* ---------------------------------------------------------------- */
/*  Komponen kecil                                                   */
/* ---------------------------------------------------------------- */

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
private fun Avatar(name: String, size: Int = 32) {
    val hue = remember(name) { (name.hashCode().toLong().and(0x7fffffff) % 360).toFloat() }
    val bg = remember(hue) { Color.hsv(hue, 0.45f, 0.65f) }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg),
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
private fun MessageItem(message: ChatMessage) {
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
                Text(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
