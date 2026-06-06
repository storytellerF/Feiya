package com.storyteller_f.feiya.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.storyteller_f.feiya.R
import com.storyteller_f.feiya.allIp
import com.storyteller_f.feiya.service.AppService
import java.util.Collections

@Composable
fun ShowQrCode(
    sub: String,
    port: String,
    modifier: Modifier = Modifier,
    sendText: (String) -> Unit = {}
) {
    val width = 200
    var selectedIp by remember {
        mutableStateOf(AppService.DEFAULT_ADDRESS)
    }
    val url by remember {
        derivedStateOf {
            "http://$selectedIp:$port/$sub"
        }
    }
    val image by remember {
        derivedStateOf {
            url.createQRImage(width, width)
        }
    }
    var expanded by remember { mutableStateOf(false) }

    val ipList by produceState(initialValue = listOf(AppService.DEFAULT_ADDRESS)) {
        value = allIp()
    }
    var quickSelectData by remember {
        mutableStateOf("")
    }
    LaunchedEffect(key1 = ipList) {
        ipList.firstOrNull {
            it.startsWith("192.168.")
        }?.let {
            quickSelectData = it
        }
    }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val (size, qrcodeHorLayout) = computeQrcodeLayout()
    val updateDropMenu = { it: Boolean ->
        expanded = it
    }
    val updateIp = { it: String, quick: String ->
        selectedIp = it
        quickSelectData = quick
    }
    if (qrcodeHorLayout) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QrCode(image, size)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CodeInfo(
                    selectedIp,
                    updateDropMenu,
                    quickSelectData,
                    updateIp,
                    clipboardManager,
                    url,
                    context,
                    sendText,
                    expanded,
                    ipList
                )
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier.fillMaxWidth()
        ) {
            QrCode(image, size)
            CodeInfo(
                selectedIp,
                updateDropMenu,
                quickSelectData,
                updateIp,
                clipboardManager,
                url,
                context,
                sendText,
                expanded,
                ipList
            )
        }
    }


}

@Composable
fun computeQrcodeLayout(): Pair<Int, Boolean> {
    val size = LocalConfiguration.current.smallestScreenWidthDp - 200
    val height = LocalConfiguration.current.screenHeightDp
    val qrcodeHorLayout = height < size * 2
    return Pair(size, qrcodeHorLayout)
}

@Composable
private fun CodeInfo(
    selectedIp: String,
    updateDropMenu: (Boolean) -> Unit,
    quickSelectData: String,
    updateIp: (String, String) -> Unit,
    clipboardManager: ClipboardManager,
    url: String,
    context: Context,
    sendText: (String) -> Unit,
    expanded: Boolean,
    ipList: List<String>
) {
    Text(
        text = selectedIp, modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)
            )
            .clickable {
                updateDropMenu(true)
            }
            .padding(8.dp), fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer
    )
    if (quickSelectData.isNotEmpty())
        Button(
            onClick = {
                updateIp(quickSelectData, "")
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text(text = stringResource(id = R.string.quick_selected_ip, quickSelectData))
        }
    val stringResource by rememberUpdatedState(newValue = stringResource(R.string.copied))
    Button(onClick = {
        clipboardManager.setText(AnnotatedString(url))
        Toast.makeText(context, stringResource, Toast.LENGTH_SHORT).show()
    }) {
        Text(text = stringResource(R.string.copy_link))
    }
    Button(onClick = {
        sendText(url)
    }) {
        Text(text = "通过蓝牙发送")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { updateDropMenu(false) }) {
        ipList.forEach {
            DropdownMenuItem(text = {
                Text(text = it)
            }, onClick = {
                updateIp(it, quickSelectData)
                updateDropMenu(false)
            })
        }
    }
}

@Composable
private fun QrCode(image: Bitmap, size: Int) {
    Image(
        bitmap = image.asImageBitmap(),
        contentDescription = stringResource(R.string.qrcode),
        modifier = Modifier.size(size.dp)
    )
}

fun String.createQRImage(width: Int, height: Int): Bitmap {
    val bitMatrix = QRCodeWriter().encode(
        this,
        BarcodeFormat.QR_CODE,
        width,
        height,
        Collections.singletonMap(EncodeHintType.CHARACTER_SET, "utf-8")
    )
    return Bitmap.createBitmap(
        (0 until height).flatMap { h: Int ->
            (0 until width).map { w: Int ->
                if (bitMatrix[w, h]
                ) Color.BLACK else Color.WHITE
            }
        }.toIntArray(),
        width, height, Bitmap.Config.ARGB_8888
    )
}