package com.storyteller_f.feiya

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.storyteller_f.feiya.ui.components.ComposeBluetoothDevice

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

interface BluetoothHidController {
    fun refreshBondDevices()

    fun connectDevice(address: String): Boolean

    fun disconnectDevice(address: String): Boolean

    suspend fun sendText(content: String): Boolean

    fun start()

    @Composable
    fun state(): State<HidState>
    fun permissionChanged()
}

class NoOpBluetoothHidController : BluetoothHidController {
    override fun refreshBondDevices() = Unit

    override fun connectDevice(address: String) = false
    override fun disconnectDevice(address: String) = false

    override suspend fun sendText(content: String) = false
    override fun start() = Unit

    @Composable
    override fun state(): State<HidState> {
        return remember {
            mutableStateOf(HidState.BluetoothOff)
        }
    }

    override fun permissionChanged() {

    }
}

@RequiresApi(Build.VERSION_CODES.P)
class BluetoothHidControllerImpl(activity: MainActivity) : BluetoothHidController {
    private val context = WeakReference(activity)
    private val bluetoothManager: BluetoothManager =
        activity.getSystemService(BluetoothManager::class.java)
    private val adapter = bluetoothManager.adapter
    private var bluetoothState by mutableStateOf(adapter?.isEnabled ?: false)
    private var bondDevices by mutableStateOf(
        activity.alreadyBondedDevices(bluetoothManager)
    )
    private var bluetoothPermissionIndex by mutableIntStateOf(0)

    private val channel = Channel<String>()
    private var connectedDevice by mutableStateOf<BluetoothDevice?>(null)
    private var connecting by mutableStateOf<String?>(null)

    var hidDevice: BluetoothHidDevice? = null
    var hidRegistered: Boolean = false

    @RequiresApi(Build.VERSION_CODES.P)
    val bluetoothProfileConnection = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(p0: Int, p1: BluetoothProfile?) {
            Log.d(TAG, "onServiceConnected() called with: p0 = $p0, p1 = $p1 $hidRegistered")
            if (p0 == BluetoothProfile.HID_DEVICE && p1 != null) {
                val bluetoothHidDevice = p1 as BluetoothHidDevice
                hidDevice = bluetoothHidDevice
                if (!hidRegistered) {
                    context.get()?.registerAsHid(bluetoothHidDevice, registerCallback)
                }
            }
        }

        override fun onServiceDisconnected(p0: Int) {
            Log.d(TAG, "onServiceDisconnected() called with: p0 = $p0")
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    val registerCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            Log.d(
                TAG,
                "onAppStatusChanged() called with: pluggedDevice = $pluggedDevice, registered = $registered"
            )
            hidRegistered = registered
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            Log.d(TAG, "onConnectionStateChanged() called with: device = $device, state = $state")
            connectedDevice = if (state == BluetoothProfile.STATE_CONNECTED) device else null
            connecting = if (state == BluetoothProfile.STATE_CONNECTING) device?.address else null
        }
    }

    override fun start() {
        Log.d(TAG, "start() called $hidRegistered $hidDevice")
        val context = context.get() ?: return
        adapter?.getProfileProxy(
            context,
            bluetoothProfileConnection,
            BluetoothProfile.HID_DEVICE
        )
        val bluetoothStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                Log.d(TAG, "onReceive() called with: context = $context, intent = $intent")
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val intExtra = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)
                        Log.i(TAG, "onReceive: $intExtra")
                        bluetoothState = intExtra == BluetoothAdapter.STATE_ON
                    }

                    BluetoothDevice.ACTION_FOUND -> {
                        val device = bluetoothDevice(intent)
                        if (device != null && context.isBonded(device)) {
                            bondDevices += device
                        }
                    }
                }
            }
        }
        val intentFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply {
            addAction(BluetoothDevice.ACTION_FOUND)
        }
        context.registerReceiver(bluetoothStateReceiver, intentFilter)
        context.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                super.onDestroy(owner)
                context.unregisterReceiver(bluetoothStateReceiver)
                context.unRegisterAsHid(hidDevice)
                bluetoothManager.closeBluetoothProfile(hidDevice)
            }
        })
        context.lifecycleScope.launch {
            for (s in channel) {
                val hid = hidDevice ?: continue
                val device = connectedDevice ?: continue
                s.toKeyCode { code ->
                    context.sendReport(hid, device, code.second, code.first)
                }
            }
        }
    }

    private fun bluetoothDevice(intent: Intent) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    @Composable
    override fun state(): State<HidState> {
        return produceState<HidState>(
            initialValue = HidState.BluetoothOff,
            bluetoothPermissionIndex,
            bluetoothState,
            connectedDevice,
            bondDevices,
            connecting,
        ) {
            val connected = connectedDevice
            val context = context.get()
            value = when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> HidState.NotSupport
                context == null -> HidState.NotSupport
                !bluetoothState -> HidState.BluetoothOff
                !context.permissionOk() -> HidState.NoPermission
                connected == null -> HidState.NoBond(bondDevices.map {
                    it.composeBluetoothDevice()
                }, connecting)

                else -> HidState.Done(connected.composeBluetoothDevice())
            }
        }
    }

    override fun permissionChanged() {
        refreshBondDevices()
        bluetoothPermissionIndex = bluetoothPermissionIndex.inc()
    }

    override fun connectDevice(address: String): Boolean {
        val context = context.get() ?: return false
        val result = context.connectDevice(hidDevice, bondDevices, address)
        when (result) {
            is BluetoothAction.Done -> if (!result.result) showShortToast(result.message)
            is BluetoothAction.NotSupport -> showShortToast("不支持")
            BluetoothAction.PermissionDenied -> showShortToast("未授权")
        }
        return (result as? BluetoothAction.Done)?.result == true
    }

    private fun showShortToast(message: String) = context.get().let {
        Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
    }

    override fun disconnectDevice(address: String): Boolean {
        val context = context.get() ?: return false
        return context.disconnectDevice(hidDevice, bondDevices, address)
    }

    override suspend fun sendText(content: String): Boolean {
        val hid = hidDevice
        val device = connectedDevice
        return if (hid != null && device != null) {
            channel.send(content)
            true
        } else false
    }

    override fun refreshBondDevices() {
        val context = context.get() ?: return
        bondDevices += context.alreadyBondedDevices(bluetoothManager)
    }

    companion object {
        private const val TAG = "BluetoothHidControllerImpl"
    }
}

@SuppressLint("MissingPermission")
fun BluetoothDevice.composeBluetoothDevice() =
    ComposeBluetoothDevice(name, address)