package com.storyteller_f.feiya

import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.os.Build
import androidx.compose.runtime.mutableStateMapOf
import com.storyteller_f.feiya.KeyboardInterceptor.Companion.qwerty

val keyboardInterceptor = mutableStateMapOf<String, KeyboardInterceptor>()

interface KeyboardInterceptor {
    fun intercept(data: String): String

    companion object {
        @Suppress("SpellCheckingInspection")
        const val qwerty = "-=qwertyuiop[]asdfghjkl;'zxcvbnm,./"
    }
}

interface KeyboardInterfaceInterceptor : KeyboardInterceptor {
    fun reflect(data: String, from: String, to: String) = data.map {
        val indexOf = from.indexOf(it)
        if (indexOf >= 0) {
            to[indexOf]
        } else it
    }.joinToString("")

    companion object {
        const val key = "keyboard style"
    }
}

object Dvorak : KeyboardInterfaceInterceptor {
    @Suppress("SpellCheckingInspection")
    private const val dvorak = "']x,doktfgsr-=a;hyujcvpzq/bi.nlmwe["

    override fun intercept(data: String): String {
        assert(qwerty.length == dvorak.length)
        return reflect(data, qwerty, dvorak)
    }
}

object Colemak : KeyboardInterfaceInterceptor {
    @Suppress("SpellCheckingInspection")
    private const val colemak = "-=qwksfoil;r[]adgethynup'zxcvbjm,./"
    override fun intercept(data: String): String {
        return reflect(data, qwerty, colemak)
    }
}

fun BluetoothManager.closeBluetoothProfile(
    bluetoothHidDevice: BluetoothHidDevice?
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, bluetoothHidDevice)
    }
}
