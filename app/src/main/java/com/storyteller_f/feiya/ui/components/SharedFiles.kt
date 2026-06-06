package com.storyteller_f.feiya.ui.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.storyteller_f.feiya.service.SharedFileInfo
import com.storyteller_f.feiya.ui.theme.AppTheme
import com.storyteller_f.feiya.R

class MainProvider : PreviewParameterProvider<List<SharedFileInfo>> {
    override val values: Sequence<List<SharedFileInfo>>
        get() = sequenceOf(ShareFilePreviewProvider().values.toList())

}

@Preview
@Composable
fun SharedFiles(
    @PreviewParameter(MainProvider::class) infoList: List<SharedFileInfo>,
    deleteItem: (SharedFileInfo) -> Unit = {},
    saveToLocal: (SharedFileInfo) -> Unit = {},
    viewInfo: (SharedFileInfo) -> Unit = {},
) {
    LazyColumn(content = {
        items(infoList, key = {
            it.uri
        }) {
            SharedFile(it, true, deleteItem, saveToLocal, viewInfo)
        }
    })
}

class ShareFilePreviewProvider : PreviewParameterProvider<SharedFileInfo> {
    override val values: Sequence<SharedFileInfo>
        get() = sequenceOf(SharedFileInfo("http://test.com", "world"), SharedFileInfo("user1", "hello"))

}

@Preview
@Composable
fun SharedFile(
    @PreviewParameter(ShareFilePreviewProvider::class) info: SharedFileInfo,
    allowView: Boolean = false,
    deleteItem: (SharedFileInfo) -> Unit = {},
    saveToLocal: (SharedFileInfo) -> Unit = {},
    viewInfo: (SharedFileInfo) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier
        .clickable {
            expanded = true
        }
        .fillMaxWidth()
        .padding(10.dp)) {
        Text(text = info.name, style = MaterialTheme.typography.titleMedium)
        Text(text = info.uri, style = MaterialTheme.typography.labelSmall)

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(text = {
                Text(text = stringResource(R.string.delete))
            }, onClick = {
                expanded = false
                deleteItem(info)
            })
            val uri = Uri.parse(info.uri)
            if (uri.scheme != "file")
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.save_to_local)) },
                    onClick = {
                        expanded = false
                        saveToLocal(info)
                    })
            if (allowView)
                DropdownMenuItem(text = {
                    Text(text = stringResource(R.string.view))
                }, onClick = {
                    expanded = false
                    viewInfo(info)
                })
        }
    }

}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AppTheme {
        SharedFiles(ShareFilePreviewProvider().values.toList())
    }
}