package com.storyteller_f.feiya

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Application context only: Android resources must not be owned by a notification-created Activity. */
@SuppressLint("MissingPermission")
class AndroidHidPlatform(context: Context) : HidPlatform {
    private val context = context.applicationContext
    private val adapter = this.context.getSystemService(BluetoothManager::class.java)?.adapter
    private val keyboard = RawHidKeyboard()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val acquisitions = MutableStateFlow<Set<HidProfileAcquisition>>(emptySet())

    override fun environment(): HidEnvironment {
        val bluetooth = adapter ?: return HidEnvironment.Unsupported
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED) {
            acquisitions.value.forEach { it.invalidate() }
            return HidEnvironment.PermissionRequired
        }
        if (!bluetooth.isEnabled) return HidEnvironment.BluetoothOff
        return HidEnvironment.Available(bluetooth.bondedDevices.map { it.toHidDevice() })
    }

    override fun observe(changed: () -> Unit): AutoCloseable {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED &&
                    intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) != BluetoothAdapter.STATE_ON) {
                    acquisitions.value.forEach { it.serviceLost() }
                }
                changed()
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        // Bluetooth broadcasts can originate from the privileged Bluetooth process, not system UID.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        return AutoCloseable {
            context.unregisterReceiver(receiver)
        }
    }

    override fun openProfile(delivered: (HidProfileAcquisition, HidProfileHandle) -> Unit): HidProfileAcquisition? {
        val bluetooth = adapter ?: return null
        val acquisition = HidProfileAcquisition(delivered,
            dispose = { handle -> cleanupScope.launch { handle.close() } },
            cancelled = { request -> acquisitions.update { it - request } },
        )
        acquisitions.update { it + acquisition }
        val accepted = try {
            bluetooth.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        acquisition.deliver(HidProfileHandle(AndroidProfile(bluetooth, proxy as BluetoothHidDevice) {
                            acquisitions.update { it - acquisition }
                        }, Dispatchers.IO, keyboard))
                    }
                }
                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HID_DEVICE) acquisition.serviceLost()
                }
            }, BluetoothProfile.HID_DEVICE)
        } catch (error: Exception) {
            acquisition.cancel()
            acquisitions.update { it - acquisition }
            throw error
        }
        if (!accepted) {
            acquisition.cancel()
            acquisitions.update { it - acquisition }
            return null
        }
        return acquisition
    }

    private inner class AndroidProfile(
        private val bluetooth: BluetoothAdapter,
        private val hid: BluetoothHidDevice,
        private val closed: () -> Unit,
    ) : HidProfileDriver {
        override val identity: Any get() = hid
        override fun register(callback: HidRegistrationCallback): Boolean {
            val sdp = BluetoothHidDeviceAppSdpSettings(
                context.getString(R.string.hid_app_name), "auto input address", "storytellerF",
                BluetoothHidDevice.SUBCLASS1_COMBO, context.readHidDescriptor(),
            )
            return hid.registerApp(sdp, null, null, ContextCompat.getMainExecutor(context),
                object : BluetoothHidDevice.Callback() {
                    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                        callback.registered(registered)
                    }

                    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                        if (device == null) return
                        val connection = when (state) {
                            BluetoothProfile.STATE_CONNECTED -> HidDeviceConnection.CONNECTED
                            BluetoothProfile.STATE_CONNECTING -> HidDeviceConnection.CONNECTING
                            BluetoothProfile.STATE_DISCONNECTING,
                            BluetoothProfile.STATE_DISCONNECTED -> HidDeviceConnection.DISCONNECTED
                            else -> return
                        }
                        // Names require permission and are read by connectedDevices/environment on IO.
                        callback.connection(HidDevice(device.address, device.address), connection)
                    }
                })
        }

        override fun unregister(): Boolean = hid.unregisterApp()
        override fun connectedDevices(): List<HidDevice> = hid.connectedDevices.map { it.toHidDevice() }
        override fun connect(device: HidDevice): Boolean = hid.connect(bluetooth.getRemoteDevice(device.address))
        override fun disconnect(device: HidDevice): Boolean = hid.disconnect(bluetooth.getRemoteDevice(device.address))

        override fun report(device: HidDevice, bytes: ByteArray): Boolean =
            hid.sendReport(bluetooth.getRemoteDevice(device.address), 2, bytes)
        override fun close() {
            bluetooth.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
            closed()
        }
    }

    private fun BluetoothDevice.toHidDevice() = HidDevice(name ?: address, address)
}
