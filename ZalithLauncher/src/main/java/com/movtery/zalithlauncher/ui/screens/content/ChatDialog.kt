package com.movtery.zalithlauncher.ui.screens.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.chat.ChatManager
import com.movtery.zalithlauncher.chat.ChatMessage
import com.movtery.zalithlauncher.chat.ChatState
import com.movtery.zalithlauncher.game.account.AccountsManager
import kotlinx.coroutines.delay

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
            shadowElevation = 3.dp
        ) {
            if (roomCode == null) {
                JoinContent(playerName = playerName, onClose = onDismiss)
            } else {
                RoomContent(code = roomCode.orEmpty(), onClose = onDismiss)
            }
        }
    }
}

@Composable
private fun JoinContent(playerName: String, onClose: () -> Unit) {
    val error by ChatManager.error.collectAsStateWithLifecycle()
    var codeInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(all = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f),
                text = "Chat grup",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onClose) { Text("Tutup") }
        }

        Text(
            text = "Masuk sebagai $playerName. Pesan tidak disimpan dan hilang saat kamu keluar.",
            style = MaterialTheme.typography.bodySmall
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { ChatManager.createRoom(playerName) }
        ) {
            Text("Buat grup baru")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = codeInput,
                onValueChange = { value ->
                    codeInput = value.uppercase()
                        .filter { it.isLetterOrDigit() }
                        .take(ChatManager.CODE_LENGTH)
                },
                label = { Text("Kode grup") },
                singleLine = true
            )
            Button(
                enabled = codeInput.length == ChatManager.CODE_LENGTH,
                onClick = { ChatManager.joinRoom(codeInput, playerName) }
            ) {
                Text("Gabung")
            }
        }

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RoomContent(code: String, onClose: () -> Unit) {
    val state by ChatManager.state.collectAsStateWithLifecycle()
    val messages by ChatManager.messages.collectAsStateWithLifecycle()
    val error by ChatManager.error.collectAsStateWithLifecycle()
    val lastSentAt by ChatManager.lastSentAt.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var input by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val remainingSec = ((ChatManager.COOLDOWN_MS - (now - lastSentAt) + 999) / 1000).coerceAtLeast(0)

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(all = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Grup: $code", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when (state) {
                        ChatState.Connecting -> "Menyambung..."
                        ChatState.Connected -> "Tersambung"
                        ChatState.Error -> error ?: "Terputus"
                        ChatState.Idle -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state == ChatState.Error) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("Salin kode") }
            TextButton(onClick = { ChatManager.leave() }) { Text("Keluar") }
            TextButton(onClick = onClose) { Text("Tutup") }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageItem(message)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = input,
                onValueChange = { input = it.take(300) },
                placeholder = { Text("Tulis pesan...") },
                singleLine = true
            )
            Button(
                enabled = state == ChatState.Connected && remainingSec == 0L && input.isNotBlank(),
                onClick = {
                    if (ChatManager.sendMessage(input)) input = ""
                }
            ) {
                Text(if (remainingSec > 0) "Kirim ($remainingSec)" else "Kirim")
            }
        }
    }
}

@Composable
private fun MessageItem(message: ChatMessage) {
    if (message.isSystem) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = message.text,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val mine = message.senderId == ChatManager.clientId
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .background(
                    color = if (mine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (mine) "Kamu" else message.sender,
                    style = MaterialTheme.typography.labelMedium
                )
                if (message.isAdmin) {
                    Text(
                        text = "✓ Admin",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(text = message.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
