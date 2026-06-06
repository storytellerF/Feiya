package com.storyteller_f.feiya.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import coil.compose.AsyncImage
import com.storyteller_f.feiya.R
import com.storyteller_f.feiya.service.Message
import com.storyteller_f.feiya.service.getAvatarIcon
import dev.jeziellago.compose.markdowntext.MarkdownText

class MessagesProvider : PreviewParameterProvider<Message> {
    override val values: Sequence<Message>
        get() = sequenceOf(Message("system", "hello"), Message("user0", "world"))

}

const val MAX_LINE = 4

@Preview
@Composable
fun MessageItem(@PreviewParameter(MessagesProvider::class) item: Message) {
    var expanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var maxLines by remember {
        mutableIntStateOf(MAX_LINE)
    }
    val measurer = rememberTextMeasurer()
    val lineCount by remember {
        derivedStateOf {
            measurer.measure(item.data).lineCount
        }
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

        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = item.from)
            MarkdownText(markdown = item.data, maxLines = maxLines)
            if (lineCount > MAX_LINE) {
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
                    clipboardManager.setText(AnnotatedString(item.data))
                    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT)
                        .show()
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
    var content by remember {
        mutableStateOf(initMessage)
    }
    val scrollState = rememberScrollState()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    if (windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f),
            ) {
                LazyColumn(modifier = Modifier.padding(bottom = 8.dp)) {
                    items(messageList.size) {
                        MessageItem(item = messageList[it])
                    }
                }
                MarkdownText(
                    markdown = content,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(8.dp)
                        .verticalScroll(scrollState)
                )
            }

            InputGroup(content = content, onValueChange = {
                content = it
            }, sendMessage = sendMessage)
        }

    } else {
        Row(modifier = Modifier.padding(20.dp)) {
            LazyColumn(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .weight(1f)
            ) {
                items(messageList.size) {
                    MessageItem(item = messageList[it])
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                MarkdownText(
                    markdown = content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(8.dp)
                        .verticalScroll(scrollState)
                        .weight(1f)
                )
                InputGroup(content = content, onValueChange = {
                    content = it
                }, sendMessage = sendMessage)
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
            value = content, onValueChange = {
                onValueChange(it)
            }, modifier = Modifier
                .weight(1f)
                .heightIn(max = 100.dp)
        )
        Button(onClick = {
            sendMessage(content)
            onValueChange("")
        }, modifier = Modifier.padding(start = 8.dp)) {
            Text(text = stringResource(R.string.send))
        }
    }
}