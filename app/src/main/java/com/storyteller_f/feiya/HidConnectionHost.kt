package com.storyteller_f.feiya

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class HidDevice(val name: String, val address: String)

enum class HidFailure { PROFILE, REGISTRATION, CONNECTION, DISCONNECTION, CLEANUP }

sealed interface HidState {
    data object NotSupport : HidState
    data object BluetoothOff : HidState
    data object NoPermission : HidState
    data object Preparing : HidState
    data class Failed(val reason: HidFailure) : HidState
    data class NoBond(
        val bondDevices: List<HidDevice>,
        val connecting: String? = null,
        val failure: HidFailure? = null,
    ) : HidState
    data class Done(val device: HidDevice, val failure: HidFailure? = null) : HidState
}

sealed interface HidEnvironment {
    data object Unsupported : HidEnvironment
    data object PermissionRequired : HidEnvironment
    data object BluetoothOff : HidEnvironment
    data class Available(val devices: List<HidDevice>) : HidEnvironment
}

enum class HidDeviceConnection { CONNECTING, CONNECTED, DISCONNECTED }

interface HidPlatform {
    fun environment(): HidEnvironment
    fun observe(changed: () -> Unit): AutoCloseable
    fun openProfile(delivered: (HidProfileAcquisition, HidProfileHandle) -> Unit): HidProfileAcquisition?
}

/** Application-owned coordinator. Platform handles, not this queue, own send validity. */
class HidConnectionHost(
    coordination: CoroutineDispatcher,
    private val io: CoroutineDispatcher,
    private val platform: HidPlatform,
    private val timeoutMillis: Long = 10_000,
    private val logFailure: (String, Throwable?) -> Unit = { _, _ -> },
) : AutoCloseable {
    private sealed interface Phase {
        data object Idle : Phase
        class Opening(val request: HidProfileAcquisition) : Phase
        class Registering(val handle: HidProfileHandle) : Phase
        class Ready(val handle: HidProfileHandle, val connecting: HidLink.Connecting? = null) : Phase
        class Releasing(val handle: HidProfileHandle, val failure: HidFailure?, val timedOut: Boolean = false) : Phase
        class Failed(val reason: HidFailure) : Phase
        data object Closed : Phase
    }
    private val scope = CoroutineScope(SupervisorJob() + coordination)
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow<HidState>(HidState.Preparing)
    val state = mutableState.asStateFlow()
    private val mutableConnection = MutableStateFlow<HidKeyboardSession?>(null)
    val keyboardConnection = mutableConnection.asStateFlow()
    private val clients = mutableSetOf<Any>()
    private var observation: AutoCloseable? = null
    private var environment: HidEnvironment = HidEnvironment.Unsupported
    private var phase: Phase = Phase.Idle
    private var deadline: Job? = null
    private var collector: Job? = null
    private var closing = false
    private var actionFailure: HidFailure? = null

    init {
        scope.launch {
            for (command in commands) try { command() } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logFailure("HID operation failed", error)
                if (error is SecurityException) environment = HidEnvironment.PermissionRequired
                val handle = handle()
                if (handle != null) {
                    handle.invalidate()
                    transition(Phase.Releasing(handle, HidFailure.PROFILE, timedOut = true))
                } else {
                    (phase as? Phase.Opening)?.request?.cancel()
                    transition(Phase.Failed(HidFailure.PROFILE))
                }
                publish()
            }
        }
    }
    private fun post(command: suspend () -> Unit) { commands.trySend(command) }
    fun foreground(client: Any) = post {
        if (closing) return@post
        clients.add(client)
        if (observation == null) observation = withContext(io) { platform.observe(::refresh) }
        refreshEnvironment(true)
    }
    fun background(client: Any) = post { clients.remove(client) }
    fun refresh() = post { if (!closing) refreshEnvironment(false) }
    fun retry() = post { if (!closing) refreshEnvironment(true) }

    private fun handle(): HidProfileHandle? = when (val current = phase) {
        is Phase.Registering -> current.handle
        is Phase.Ready -> current.handle
        is Phase.Releasing -> current.handle
        else -> null
    }
    private fun transition(next: Phase) {
        deadline?.cancel()
        phase = next
        if (next is Phase.Opening || next is Phase.Registering || next is Phase.Ready && next.connecting != null || next is Phase.Releasing && !next.timedOut) {
            deadline = scope.launch {
                delay(timeoutMillis)
                post {
                    if (phase !== next) return@post
                    when (next) {
                        is Phase.Opening -> { next.request.cancel(); transition(Phase.Failed(HidFailure.PROFILE)) }
                        is Phase.Registering -> release(next.handle, HidFailure.REGISTRATION)
                        is Phase.Ready -> {
                            val attempt = next.connecting
                            if (attempt != null && next.handle.cancelConnection(attempt)) {
                                logFailure("HID connection timed out", null)
                                release(next.handle, HidFailure.CONNECTION)
                            }
                        }
                        is Phase.Releasing -> transition(Phase.Releasing(next.handle, next.failure, true))
                    }
                    publish()
                }
            }
        }
    }
    private suspend fun refreshEnvironment(retry: Boolean) {
        environment = withContext(io) { platform.environment() }
        if (environment !is HidEnvironment.Available) {
            (phase as? Phase.Opening)?.let { it.request.cancel(); transition(Phase.Idle) }
            handle()?.let {
                if (environment == HidEnvironment.BluetoothOff) it.serviceLost() else it.invalidate()
                release(it, null)
            }
        } else {
            val releasing = phase as? Phase.Releasing
            if (retry && releasing != null) release(releasing.handle, releasing.failure)
            if (retry && phase is Phase.Failed) transition(Phase.Idle)
            recover()
        }
        publish()
    }
    private suspend fun recover() {
        if (closing || clients.isEmpty() || environment !is HidEnvironment.Available || phase != Phase.Idle) return
        actionFailure = null
        val request = withContext(io) {
            platform.openProfile { acquisition, received ->
                val result = commands.trySend {
                    val opening = phase as? Phase.Opening
                    if (opening?.request !== acquisition || !acquisition.claim(received)) {
                        acquisition.cancel()
                        return@trySend
                    }
                    transition(Phase.Registering(received))
                    collector = scope.launch { received.state.collect { snapshot ->
                        post { if (handle() === received && received.state.value === snapshot) profileChanged(received, snapshot) }
                    } }
                    withContext(io) { received.register() }
                    profileChanged(received, received.state.value)
                }
                if (result.isFailure) acquisition.cancel()
            }
        }
        transition(if (request == null) Phase.Failed(HidFailure.PROFILE) else Phase.Opening(request))
    }
    private suspend fun profileChanged(profile: HidProfileHandle, snapshot: HidProfileState) {
        if (handle() !== profile) return
        when {
            phase is Phase.Releasing -> if (profile.canClose) finishRelease(phase as Phase.Releasing)
            snapshot is HidProfileState.Ready -> {
                if (phase is Phase.Registering) {
                    transition(Phase.Ready(profile))
                    withContext(io) { profile.restoreConnection() }
                }
                val ready = phase as? Phase.Ready
                val link = (profile.state.value as? HidProfileState.Ready)?.link
                val connecting = link as? HidLink.Connecting
                if (ready != null && ready.connecting !== connecting) transition(Phase.Ready(profile, connecting))
            }
            profile.canClose -> release(profile, if (phase is Phase.Registering) HidFailure.REGISTRATION else null)
        }
        publish()
    }
    private suspend fun release(profile: HidProfileHandle, failure: HidFailure?) {
        profile.invalidate()
        val releasing = Phase.Releasing(profile, failure)
        transition(releasing)
        withContext(io) { profile.requestRelease() }
        if (profile.canClose) finishRelease(releasing)
    }
    private suspend fun finishRelease(releasing: Phase.Releasing) {
        withContext(io) { releasing.handle.close() }
        collector?.cancel()
        collector = null
        transition(releasing.failure?.let { Phase.Failed(it) } ?: Phase.Idle)
        if (closing) finishClose() else recover()
    }
    fun connectDevice(address: String) = post {
        val current = phase as? Phase.Ready ?: return@post
        val device = (environment as? HidEnvironment.Available)?.devices?.firstOrNull { it.address == address } ?: return@post
        actionFailure = null
        if (!withContext(io) { current.handle.connect(device) }) actionFailure = HidFailure.CONNECTION
        profileChanged(current.handle, current.handle.state.value)
    }
    fun cancelConnectionAttempt() = post {
        val current = phase as? Phase.Ready ?: return@post
        val attempt = (current.handle.state.value as? HidProfileState.Ready)?.link as? HidLink.Connecting ?: return@post
        if (current.handle.cancelConnection(attempt)) release(current.handle, null)
        publish()
    }
    fun disconnectDevice(address: String) = post {
        val current = phase as? Phase.Ready ?: return@post
        val link = (current.handle.state.value as? HidProfileState.Ready)?.link as? HidLink.Connected ?: return@post
        if (link.device.address != address) return@post
        actionFailure = null
        if (!withContext(io) { current.handle.disconnect(link.device) }) {
            actionFailure = HidFailure.DISCONNECTION
            withContext(io) { current.handle.restoreConnection() }
        }
        publish()
    }
    private fun publish() {
        val link = ((phase as? Phase.Ready)?.handle?.state?.value as? HidProfileState.Ready)?.link
        mutableConnection.value = (link as? HidLink.Connected)?.session?.takeIf { it.isConnected }
        mutableState.value = when (val current = environment) {
            HidEnvironment.Unsupported -> HidState.NotSupport
            HidEnvironment.PermissionRequired -> HidState.NoPermission
            HidEnvironment.BluetoothOff -> HidState.BluetoothOff
            is HidEnvironment.Available -> when (val lifecycle = phase) {
                is Phase.Failed -> HidState.Failed(lifecycle.reason)
                is Phase.Releasing -> if (lifecycle.timedOut) HidState.Failed(HidFailure.CLEANUP) else HidState.Preparing
                is Phase.Ready -> when (link) {
                    is HidLink.Connected -> HidState.Done(current.devices.firstOrNull { it.address == link.device.address } ?: link.device, actionFailure)
                    is HidLink.Connecting -> HidState.NoBond(current.devices, link.device.address)
                    is HidLink.Disconnected -> HidState.NoBond(current.devices, failure = actionFailure ?: if (link.failed) HidFailure.CONNECTION else null)
                    null -> HidState.Preparing
                }
                else -> HidState.Preparing
            }
        }
    }
    override fun close() = post {
        closing = true
        clients.clear()
        (phase as? Phase.Opening)?.request?.cancel()
        val current = handle()
        if (current != null) release(current, null) else finishClose()
        publish()
    }
    private suspend fun finishClose() {
        withContext(io) { observation?.close() }
        observation = null
        transition(Phase.Closed)
        commands.close()
        scope.cancel()
    }
}
