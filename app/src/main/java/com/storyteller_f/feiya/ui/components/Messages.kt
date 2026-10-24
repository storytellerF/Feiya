package com.storyteller_f.feiya.ui.components

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.storyteller_f.feiya.R
import com.storyteller_f.feiya.service.Message
import com.storyteller_f.feiya.service.getAvatarIcon
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch

class MessagesProvider : PreviewParameterProvider<Message> {
    override val values: Sequence<Message>
        get() = sequenceOf(Message("system", "hello"), Message("user0", "world"))

}

const val MAX_LINE = 4

@Preview
@Composable
fun MessageItem(@PreviewParameter(MessagesProvider::class) item: Message) {
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var maxLines by remember {
        mutableIntStateOf(MAX_LINE)
    }
    Row(modifier = Modifier
        .clickable {
            expanded = true
        }
        .padding(bottom = 8.dp)
        .fillMaxWidth()) {
        AsyncImage(
            model = getAvatarIcon(item.from),
            contentDescription = item.from,
            modifier = Modifier
                .padding(top = 4.dp)
                .width(40.dp)
                .height(40.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )

        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(text = item.from)
            MarkdownText(markdown = item.data, maxLines = maxLines)
            if (item.data.length > 160 || item.data.count { it == '\n' } >= MAX_LINE) {
                Button(onClick = {
                    maxLines = if (maxLines == MAX_LINE) {
                        Int.MAX_VALUE
                    } else {
                        MAX_LINE
                    }
                }) {
                    Text(text = if (maxLines == MAX_LINE) "Show All" else "Close")
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(id = android.R.string.copy)) },
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", item.data)))
                        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                    }
                    expanded = false
                })
        }
    }
}

class MessageContentProvider : PreviewParameterProvider<List<Message>> {
    override val values: Sequence<List<Message>>
        get() = sequenceOf(MessagesProvider().values.toList())

}

@Preview(device = Devices.TABLET)
@Preview(device = Devices.PHONE)
@Composable
fun MessagePage(
    @PreviewParameter(MessageContentProvider::class) messageList: List<Message>,
    initMessage: String = "",
    sendMessage: (String) -> Unit = {}
) {
    var content by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initMessage) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val wide = maxWidth >= 800.dp
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (messageList.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.messages_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(messageList.size) { MessageItem(messageList[it]) }
                }
                if (!wide) InputGroup(content, { content = it }, sendMessage)
            }
            if (wide) {
                androidx.compose.material3.VerticalDivider()
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text(stringResource(R.string.message_preview), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                    MarkdownText(content, modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()))
                    InputGroup(content, { content = it }, sendMessage)
                }
            }
        }
    }
}

@Composable
fun InputGroup(content: String, onValueChange: (String) -> Unit, sendMessage: (String) -> Unit) {
    Row(
        modifier = Modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            placeholder = { Text(stringResource(R.string.message_draft)) },
            value = content, onValueChange = {
                onValueChange(it)
            }, modifier = Modifier
                .weight(1f)
                .heightIn(max = 100.dp)
        )
        Button(onClick = {
            sendMessage(content)
            onValueChange("")
        }, enabled = content.isNotBlank(), modifier = Modifier.padding(start = 8.dp)) {
            Text(text = stringResource(R.string.send))
        }
    }
}
