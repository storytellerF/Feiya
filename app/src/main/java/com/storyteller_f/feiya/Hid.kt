package com.storyteller_f.feiya

import android.content.Context

fun Context.readHidDescriptor(): ByteArray {
    return assets.open("hid").use { stream ->
        stream.bufferedReader().lineSequence().flatMap { line ->
            val lastCommas = line.lastIndexOf(", ")
            line.substring(0, lastCommas).split(", ").map {
                it.substring(2).toInt(16).toByte()
            }
        }.toList().toByteArray()
    }
}
