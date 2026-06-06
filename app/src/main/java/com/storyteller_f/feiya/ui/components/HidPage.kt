package com.storyteller_f.feiya.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storyteller_f.feiya.Colemak
import com.storyteller_f.feiya.Dvorak
import com.storyteller_f.feiya.HidState
import com.storyteller_f.feiya.KeyboardInterfaceInterceptor
import com.storyteller_f.feiya.R
import com.storyteller_f.feiya.keyboardInterceptor

class ComposeBluetoothDevice(val name: String, val address: String)

@Composable
fun BoundDevice(device: ComposeBluetoothDevice, connectDevice: (String) -> Boolean) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .clickable {
                connectDevice(device.address)
            }
            .background(
                MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(8.dp)

    ) {
        val modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
        Text(
            text = device.name,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = modifier
        )
        Text(
            text = device.address,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = modifier
        )
    }
}

class HidPreviewProvider : PreviewParameterProvider<HidState> {
    override val values: Sequence<HidState>
        get() = sequenceOf(
            HidState.BluetoothOff, HidState.NoPermission, HidState.NoBond(listOf()), HidState.Done(
                ComposeBluetoothDevice("name", "address")
            )
        )

}

@Preview
@Composable
fun HidScreen(
    @PreviewParameter(HidPreviewProvider::class) bluetoothState: HidState,
    requestPermission: () -> Unit = {},
    connectDevice: (String) -> Boolean = { false },
    sendText: (String) -> Unit = {},
    disconnect: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val toBluetoothSettings = {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        context.startActivity(intent)
    }

    val rootModifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
    when (bluetoothState) {
        HidState.NotSupport -> NotSupportPage()
        HidState.BluetoothOff -> BluetoothOffPage()
        HidState.NoPermission -> NoPermissionPage(requestPermission)

        is HidState.NoBond -> EmptyBoundPage(
            rootModifier,
            bluetoothState,
            toBluetoothSettings,
            connectDevice
        )

        is HidState.Done -> ConnectedPage(rootModifier, bluetoothState, sendText) {
            disconnect(bluetoothState.device.address)
        }
    }
}

@Composable
private fun NotSupportPage() {
    OneCenter {
        Text(text = "not support")
    }
}

@Composable
private fun BluetoothOffPage() {
    OneCenter {
        Text(text = stringResource(R.string.bluetooth_off_tip))
    }
}

@Composable
private fun NoPermissionPage(requestPermission: () -> Unit) {
    OneCenter {
        Button(onClick = {
            requestPermission()
        }) {
            Text(text = stringResource(R.string.bluetooth_permission_tip))
        }
    }
}

@Composable
private fun ConnectedPage(
    modifier: Modifier,
    bluetoothState: HidState.Done,
    sendText: (String) -> Unit,
    disconnect: () -> Unit,
) {
    var content by remember {
        mutableStateOf("")
    }
    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = stringResource(
                id = R.string.connected_device_tip,
                bluetoothState.device.name
            ), style = MaterialTheme.typography.titleMedium
        )
        Button(onClick = {
            disconnect()
        }) {
            Text(text = "断开连接")
        }
        Text(text = stringResource(R.string.test_case))
        Row {
            Button(onClick = {
                sendText("serviceBinder")
            }) {
                Text(text = "serviceBinder")
            }
            if (bluetoothState.device.name.contains("Mac")) {
                Button(onClick = {
                    sendText("z")
                }) {
                    Text(text = "z")
                }
                Button(onClick = {
                    sendText("/")
                }) {
                    Text(text = "/")
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(value = content, onValueChange = {
                content = it
            }, modifier = Modifier.weight(1f))
            Button(onClick = {
                sendText(content)
            }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(text = stringResource(id = R.string.send))
            }
        }
        val interceptor = keyboardInterceptor[KeyboardInterfaceInterceptor.key]
        if (interceptor == null) {
            Column {
                listOf(Dvorak, Colemak).forEach {
                    Button(onClick = {
                        keyboardInterceptor[KeyboardInterfaceInterceptor.key] = it
                    }) {
                        Text(
                            text = stringResource(
                                R.string.plug_dvorak_keyboard_style,
                                it.javaClass.simpleName
                            )
                        )
                    }
                }
            }
        } else {
            Button(onClick = {
                keyboardInterceptor.remove(KeyboardInterfaceInterceptor.key)
            }) {
                Text(
                    text = stringResource(
                        R.string.unplug_dvorak_keyboard_style,
                        interceptor.javaClass.simpleName
                    )
                )
            }
        }
    }
}

@Composable
private fun EmptyBoundPage(
    modifier: Modifier,
    bluetoothState: HidState.NoBond,
    toBluetoothSettings: () -> Unit,
    connectDevice: (String) -> Boolean
) {
    val bondDevices = bluetoothState.bondDevices
    if (bondDevices.isEmpty()) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.no_bond_device_tip),
                modifier = Modifier.padding(8.dp)
            )
            Button(onClick = toBluetoothSettings) {
                Text(text = stringResource(R.string.bluetooth_pair_tip))
            }
        }
    } else {
        Column(modifier = modifier.padding(8.dp)) {
            Text(
                text = stringResource(R.string.bond_devices_tip),
                fontSize = 20.sp
            )
            Button(onClick = toBluetoothSettings) {
                Text(text = stringResource(R.string.no_expacted_bluetooth_pair_tip))
            }
            LazyColumn {
                items(bondDevices.size) {
                    BoundDevice(bondDevices[it], connectDevice)
                }
            }
        }
    }
    if (bluetoothState.connecting != null)
        AlertDialog(onDismissRequest = { }, confirmButton = { }, text = {
            Text(text = "connecting to ${bluetoothState.connecting}")
        })
}