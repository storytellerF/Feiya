package com.storyteller_f.feiya.ui.components

import android.content.ClipData
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storyteller_f.feiya.FeiyaApplication
import com.storyteller_f.feiya.QrCodeHost
import com.storyteller_f.feiya.QrCodeState
import com.storyteller_f.feiya.R
import kotlinx.coroutines.launch

@Composable
fun ShowQrCode(sub: String, port: String, modifier: Modifier = Modifier, sendText: (String) -> Unit = {}) {
    val application = LocalContext.current.applicationContext as FeiyaApplication
    val host = remember(sub, port, application) { QrCodeHost(application.serverCoordination, port, sub) }
    DisposableEffect(host) { onDispose { host.close() } }
    val state by host.state.collectAsStateWithLifecycle()
    QrCodeContent(state, host::reload, host::select, modifier, sendText)
}

@Composable
internal fun QrCodeContent(
    state: QrCodeState,
    reload: () -> Unit,
    selectAddress: (String) -> Unit,
    modifier: Modifier = Modifier,
    sendText: (String) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.failed) {
            Text(stringResource(R.string.qr_unavailable))
            TextButton(onClick = reload) { Text(stringResource(R.string.boot_setting_retry)) }
        } else if (state.image == null) CircularProgressIndicator()
        state.image?.let { bitmap ->
            Image(bitmap.asImageBitmap(), stringResource(R.string.qrcode), Modifier.widthIn(max = 220.dp).fillMaxWidth().aspectRatio(1f).background(androidx.compose.ui.graphics.Color.White).padding(8.dp))
        }
        Box {
            TextButton(onClick = { expanded = true }, enabled = state.addresses.isNotEmpty()) { Text(state.address) }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                state.addresses.forEach { address -> DropdownMenuItem(text = { Text(address) }, onClick = { selectAddress(address); expanded = false }) }
            }
        }
        if (state.url.isNotEmpty()) Text(state.url, style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            val url = state.url
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", url))) }
        }, enabled = state.image != null) { Text(stringResource(R.string.copy_link)) }
        TextButton(onClick = { sendText(state.url) }, enabled = state.image != null) { Text(stringResource(R.string.send_via_bluetooth)) }
    }
}
