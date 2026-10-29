package com.storyteller_f.feiya.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storyteller_f.feiya.HidKeyboardState
import com.storyteller_f.feiya.TargetKeyboardLayout
import com.storyteller_f.feiya.KeyboardCalibration
import com.storyteller_f.feiya.HidState
import com.storyteller_f.feiya.HidDevice
import com.storyteller_f.feiya.HidFailure
import com.storyteller_f.feiya.R

@Composable
fun BoundDevice(device: HidDevice, connectDevice: (String) -> Unit) {
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
                HidDevice("name", "address")
            )
        )

}

@Preview
@Composable
fun HidScreen(
    @PreviewParameter(HidPreviewProvider::class) bluetoothState: HidState,
    requestPermission: () -> Unit = {},
    connectDevice: (String) -> Unit = {},
    retryConnection: () -> Unit = {},
    cancelConnection: () -> Unit = {},
    sendText: (String) -> Unit = {},
    keyboardState: HidKeyboardState = HidKeyboardState(),
    cancelTask: (Long) -> Unit = {},
    cancelAll: () -> Unit = {},
    selectLayout: (TargetKeyboardLayout) -> Unit = {},
    selectCalibration: (KeyboardCalibration) -> Unit = {},
    sendLeftCalibrationKey: () -> Unit = {},
    sendRightCalibrationKey: () -> Unit = {},
    disconnect: (String) -> Unit = {},
) {
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val context = LocalContext.current
    val toBluetoothSettings = {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        context.startActivity(intent)
    }

    val rootModifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
    val connectionContent: @Composable () -> Unit = {
            when (bluetoothState) {
                HidState.NotSupport -> NotSupportPage()
                HidState.BluetoothOff -> BluetoothOffPage()
                HidState.NoPermission -> NoPermissionPage(requestPermission)
                HidState.Preparing -> OneCenter { Text(stringResource(R.string.hid_preparing)) }
                is HidState.Failed -> OneCenter {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HidFailureText(bluetoothState.reason)
                        Button(onClick = retryConnection) { Text(stringResource(R.string.hid_retry)) }
                    }
                }

                is HidState.NoBond -> EmptyBoundPage(
                    rootModifier,
                    bluetoothState,
                    toBluetoothSettings,
                    connectDevice,
                    cancelConnection,
                )

                is HidState.Done -> ConnectedPage(
                    rootModifier, bluetoothState, sendText, keyboardState, draft, { draft = it },
                    selectLayout, selectCalibration, sendLeftCalibrationKey, sendRightCalibrationKey,
                ) {
                    disconnect(bluetoothState.device.address)
                }
            }
    }
    BoxWithConstraints(rootModifier.imePadding()) {
        val taskHeight = maxHeight * 0.45f
        if (maxWidth >= 720.dp && keyboardState.tasks.isNotEmpty()) {
            Row(rootModifier) {
                Box(Modifier.weight(1f)) { connectionContent() }
                VerticalDivider()
                Column(Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    HidTaskPanel(keyboardState.tasks, cancelTask, cancelAll)
                }
            }
        } else {
            Column(rootModifier) {
                Column(Modifier.heightIn(max = taskHeight).verticalScroll(rememberScrollState())) {
                    HidTaskPanel(keyboardState.tasks, cancelTask, cancelAll)
                }
                Box(Modifier.weight(1f)) { connectionContent() }
            }
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
    keyboardState: HidKeyboardState,
    draft: TextFieldValue,
    editContent: (TextFieldValue) -> Unit,
    selectLayout: (TargetKeyboardLayout) -> Unit,
    selectCalibration: (KeyboardCalibration) -> Unit,
    sendLeftCalibrationKey: () -> Unit,
    sendRightCalibrationKey: () -> Unit,
    disconnect: () -> Unit,
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
        Text(
            text = stringResource(R.string.connected_device_tip, bluetoothState.device.name),
            style = MaterialTheme.typography.titleMedium,
        )
        bluetoothState.failure?.let { HidFailureText(it) }
        Button(onClick = disconnect) {
            Text(text = stringResource(R.string.hid_disconnect))
        }
        Text(text = stringResource(R.string.hid_target_layout))
        Text(text = stringResource(R.string.hid_target_layout_help))
        Column {
            TargetKeyboardLayout.entries.forEach { layout ->
                FilterChip(
                    selected = keyboardState.layout == layout,
                    onClick = { selectLayout(layout) },
                    label = { Text(layout.name) },
                )
            }
        }
        TextField(
            value = draft,
            onValueChange = editContent,
            label = { Text(stringResource(R.string.hid_text)) },
            modifier = Modifier.fillMaxWidth().testTag("hid_text"),
        )
        Button(onClick = { sendText(draft.text) }, enabled = draft.text.isNotEmpty(), modifier = Modifier.testTag("hid_send")) {
            Text(text = stringResource(R.string.send))
        }
        Button(onClick = { sendText("serviceBinder") }) {
            Text(text = stringResource(R.string.hid_send_test))
        }
        Text(
            text = stringResource(R.string.hid_mac_calibration),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(text = stringResource(R.string.hid_mac_calibration_help))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyboardCalibration.entries.forEach { calibration ->
                FilterChip(
                    selected = keyboardState.calibration == calibration,
                    onClick = { selectCalibration(calibration) },
                    label = { Text(calibration.name) },
                )
            }
        }
        Text(text = stringResource(R.string.hid_mac_left_step))
        Button(onClick = sendLeftCalibrationKey) {
            Text(text = stringResource(
                if (keyboardState.calibration == KeyboardCalibration.ANSI) R.string.hid_physical_z
                else R.string.hid_physical_iso,
            ))
        }
        Text(text = stringResource(R.string.hid_mac_right_step))
        Button(onClick = sendRightCalibrationKey) {
            Text(text = stringResource(R.string.hid_physical_slash))
        }
    }
}

@Composable
private fun HidFailureText(failure: HidFailure) {
    val message = when (failure) {
        HidFailure.CLEANUP -> R.string.hidCleanupFailed
        HidFailure.PROFILE -> R.string.hid_profile_failed
        HidFailure.REGISTRATION -> R.string.hid_registration_failed
        HidFailure.CONNECTION -> R.string.hid_connect_failed
        HidFailure.DISCONNECTION -> R.string.hid_disconnect_failed
    }
    Text(stringResource(message), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun EmptyBoundPage(
    modifier: Modifier,
    bluetoothState: HidState.NoBond,
    toBluetoothSettings: () -> Unit,
    connectDevice: (String) -> Unit,
    cancelConnection: () -> Unit,
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
            bluetoothState.failure?.let { HidFailureText(it) }
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
        AlertDialog(onDismissRequest = cancelConnection, confirmButton = {
            Button(onClick = cancelConnection) { Text(stringResource(R.string.cancel_action)) }
        }, text = {
            Text(text = "connecting to ${bluetoothState.connecting}")
        })
}
