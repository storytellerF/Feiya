package com.storyteller_f.feiya

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.net.NetworkInterface

suspend fun allIp(): List<String> {
    val set = mutableSetOf<String>()
    val networkInterfaces = withContext(Dispatchers.IO) {
        NetworkInterface.getNetworkInterfaces()
    }
    for (networkInterface in networkInterfaces) {
        yield()
        for (interfaceAddress in networkInterface.interfaceAddresses) {
            interfaceAddress.address.hostAddress?.let { set.add(it) }
        }
        for (inetAddress in networkInterface.inetAddresses) {
            inetAddress.hostAddress?.let { set.add(it) }
        }
    }
    return set.toList()
}