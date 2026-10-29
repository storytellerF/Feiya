package com.storyteller_f.feiya

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface HidLink {
    data class Disconnected(val failed: Boolean = false) : HidLink
    data class Connecting(val device: HidDevice) : HidLink
    class Connected(val device: HidDevice, val session: HidKeyboardSession, val identity: Any) : HidLink
}

enum class HidReleaseRequest { NOT_SENT, PENDING, REJECTED }

sealed interface HidProfileState {
    data object Idle : HidProfileState
    class Registering(val link: HidLink = HidLink.Disconnected()) : HidProfileState
    class Ready(val link: HidLink) : HidProfileState
    class Releasing(val request: HidReleaseRequest) : HidProfileState
    data object Rejected : HidProfileState
    data object Released : HidProfileState
    data object Lost : HidProfileState
    data object Closed : HidProfileState
}

interface HidRegistrationCallback {
    fun registered(registered: Boolean)
    fun connection(device: HidDevice, state: HidDeviceConnection)
}

/** Blocking platform commands; the Host calls them sequentially on IO. Callbacks may be concurrent. */
interface HidProfileDriver {
    val identity: Any get() = this
    fun register(callback: HidRegistrationCallback): Boolean
    fun unregister(): Boolean
    fun connectedDevices(): List<HidDevice>
    fun connect(device: HidDevice): Boolean
    fun disconnect(device: HidDevice): Boolean
    fun report(device: HidDevice, bytes: ByteArray): Boolean
    fun close()
}

/**
 * Owns one registration attempt and its connection generations at the platform boundary.
 * Callback transitions revoke sending synchronously, independently of the Host's event queue.
 * A handle is never registered a second time; a retry acquires a fresh handle after confirmed release.
 */
class HidProfileHandle(
    private val driver: HidProfileDriver,
    private val transport: CoroutineDispatcher,
    private val keyboard: RawHidKeyboard,
) {
    private val mutableState = MutableStateFlow<HidProfileState>(HidProfileState.Idle)
    val state = mutableState.asStateFlow()
    val platformIdentity: Any get() = driver.identity
    val canClose: Boolean get() = when (state.value) {
        HidProfileState.Idle, HidProfileState.Rejected, HidProfileState.Released,
        HidProfileState.Lost, HidProfileState.Closed -> true
        else -> false
    }

    private val callback = object : HidRegistrationCallback {
        override fun registered(registered: Boolean) {
            mutableState.update { previous ->
                when (previous) {
                    is HidProfileState.Registering -> if (registered) HidProfileState.Ready(previous.link) else HidProfileState.Released
                    is HidProfileState.Ready -> if (registered) previous else HidProfileState.Released
                    is HidProfileState.Releasing -> if (registered) previous else HidProfileState.Released
                    else -> previous
                }
            }
        }

        override fun connection(device: HidDevice, state: HidDeviceConnection) {
            mutableState.update { previous ->
                val link = when (previous) {
                    is HidProfileState.Registering -> previous.link
                    is HidProfileState.Ready -> previous.link
                    else -> return@update previous
                }
                val next = when (state) {
                    HidDeviceConnection.CONNECTED -> if (link is HidLink.Connected &&
                        link.device.address == device.address) link else newConnection(device)
                    HidDeviceConnection.CONNECTING -> if (link is HidLink.Connecting &&
                        link.device.address == device.address) link else HidLink.Connecting(device)
                    HidDeviceConnection.DISCONNECTED -> {
                        val target = when (link) {
                            is HidLink.Connected -> link.device
                            is HidLink.Connecting -> link.device
                            is HidLink.Disconnected -> null
                        }
                        if (target != null && target.address != device.address) return@update previous
                        HidLink.Disconnected(failed = link is HidLink.Connecting)
                    }
                }
                // Even a disconnected->disconnected callback changes snapshot identity, fencing queries.
                when (previous) {
                    is HidProfileState.Ready -> HidProfileState.Ready(next)
                    else -> HidProfileState.Registering(next)
                }
            }
        }
    }

    fun register(): Boolean {
        val pending = HidProfileState.Registering()
        check(mutableState.compareAndSet(HidProfileState.Idle, pending)) { "Registration already attempted" }
        val accepted = try {
            driver.register(callback)
        } catch (denied: SecurityException) {
            // Permission rejection happens before the platform accepts ownership.
            mutableState.compareAndSet(pending, HidProfileState.Rejected)
            throw denied
        }
        if (!accepted) {
            mutableState.compareAndSet(pending, HidProfileState.Rejected)
        }
        return accepted
    }

    /** Reconcile a pre-existing connection only if no callback changed the snapshot during the query. */
    fun restoreConnection() {
        val snapshot = state.value as? HidProfileState.Ready ?: return
        if (snapshot.link !is HidLink.Disconnected) return
        val device = driver.connectedDevices().firstOrNull() ?: return
        mutableState.compareAndSet(snapshot, HidProfileState.Ready(newConnection(device)))
    }

    private fun newConnection(device: HidDevice): HidLink.Connected {
        val identity = Any()
        val session = HidKeyboardSession(device.name, transport, keyboard, validity = {
            val current = state.value as? HidProfileState.Ready
            (current?.link as? HidLink.Connected)?.identity === identity
        }) { bytes -> driver.report(device, bytes) }
        return HidLink.Connected(device, session, identity)
    }

    /** Revokes all local send leases without claiming that the platform has released registration. */
    fun invalidate() {
        mutableState.update { previous ->
            when (previous) {
                is HidProfileState.Registering, is HidProfileState.Ready ->
                    HidProfileState.Releasing(HidReleaseRequest.NOT_SENT)
                HidProfileState.Idle -> HidProfileState.Released
                else -> previous
            }
        }
    }

    /** Service/radio loss is authoritative: there is no live registration left on that service. */
    fun serviceLost() {
        mutableState.update { if (it == HidProfileState.Closed) it else HidProfileState.Lost }
    }

    /** Accepted release requests await the false registration callback; retries do not send duplicates. */
    fun requestRelease(): Boolean {
        invalidate()
        val previous = state.value as? HidProfileState.Releasing ?: return canClose
        if (previous.request == HidReleaseRequest.PENDING) return true
        val pending = HidProfileState.Releasing(HidReleaseRequest.PENDING)
        if (!mutableState.compareAndSet(previous, pending)) return canClose
        val accepted = try {
            driver.unregister()
        } catch (denied: SecurityException) {
            mutableState.compareAndSet(pending, HidProfileState.Releasing(HidReleaseRequest.REJECTED))
            throw denied
        }
        if (!accepted) mutableState.compareAndSet(pending, HidProfileState.Releasing(HidReleaseRequest.REJECTED))
        return accepted || canClose
    }

    fun connect(device: HidDevice): Boolean {
        val ready = state.value as? HidProfileState.Ready ?: return false
        if (ready.link !is HidLink.Disconnected) return false
        val connecting = HidProfileState.Ready(HidLink.Connecting(device))
        if (!mutableState.compareAndSet(ready, connecting)) return false
        val accepted = driver.connect(device)
        if (!accepted) mutableState.compareAndSet(connecting, HidProfileState.Ready(HidLink.Disconnected(failed = true)))
        return accepted
    }

    /** Retire the registration: Android connection callbacks carry no attempt identifier.
     * Reusing it after cancellation could attribute a late success to a new attempt.
     */
    fun cancelConnection(attempt: HidLink.Connecting): Boolean {
        while (true) {
            val ready = state.value as? HidProfileState.Ready ?: return false
            if (ready.link !== attempt) return false
            if (mutableState.compareAndSet(ready, HidProfileState.Releasing(HidReleaseRequest.NOT_SENT))) return true
        }
    }

    fun disconnect(device: HidDevice): Boolean {
        val ready = state.value as? HidProfileState.Ready ?: return false
        val connected = ready.link as? HidLink.Connected ?: return false
        if (connected.device.address != device.address) return false
        // An explicit disconnect revokes sending before the blocking command, including if it fails.
        val disconnected = HidProfileState.Ready(HidLink.Disconnected())
        if (!mutableState.compareAndSet(ready, disconnected)) return false
        return driver.disconnect(device)
    }

    fun close() {
        check(canClose) { "Registration release has not been confirmed" }
        if (state.value == HidProfileState.Closed) return
        driver.close()
        mutableState.value = HidProfileState.Closed
    }
}
