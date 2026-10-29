package com.storyteller_f.feiya

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

enum class HidKeyResult { SENT, CONNECTION_CHANGED, FAILED }

/** Each instance identifies one connection lifetime, even when reconnecting to the same device. */
interface HidKeyboardConnection {
    val deviceName: String
    val isConnected: Boolean
    suspend fun sendRawKey(key: HidKey): HidKeyResult
}

class HidKeyboardSession(
    override val deviceName: String,
    private val transportDispatcher: CoroutineDispatcher,
    private val keyboard: RawHidKeyboard,
    private val validity: () -> Boolean = { true },
    private val report: (ByteArray) -> Boolean,
) : HidKeyboardConnection {
    private val connected = MutableStateFlow(true)
    override val isConnected: Boolean get() = connected.value && validity()

    fun disconnect() {
        connected.value = false
    }

    override suspend fun sendRawKey(key: HidKey): HidKeyResult = withContext(transportDispatcher) {
        if (!isConnected) return@withContext HidKeyResult.CONNECTION_CHANGED
        val sent = try {
            // Recheck after acquiring the raw sender's mutex, before pressing anything.
            keyboard.sendRawKey(key, canSend = { isConnected }, report = report)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        when {
            !isConnected -> HidKeyResult.CONNECTION_CHANGED
            sent -> HidKeyResult.SENT
            else -> HidKeyResult.FAILED
        }
    }
}
