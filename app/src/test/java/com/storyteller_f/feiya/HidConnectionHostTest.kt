package com.storyteller_f.feiya

import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HidConnectionHostTest {
    @Test fun repeatedForegroundSharesRegistration() = test { f ->
        f.ready(); f.host.foreground(Any()); f.host.retry(); runCurrent()
        assertEquals(1, f.requests.size); assertEquals(1, f.driver.registrations)
    }
    @Test fun connectionRequiresConfirmedRegistration() = test { f ->
        f.open(); f.host.connectDevice(device.address); runCurrent()
        assertEquals(0, f.driver.connects); assertSame(HidState.Preparing, f.host.state.value)
    }
    @Test fun callbackImmediatelyRevokesOldSessionBeforeHostRuns() = test { f ->
        f.connected(); val old = f.host.keyboardConnection.value!!
        f.driver.callback.connection(device, HidDeviceConnection.DISCONNECTED)
        f.driver.callback.connection(device, HidDeviceConnection.CONNECTED)
        assertFalse(old.isConnected)
        assertEquals(HidKeyResult.CONNECTION_CHANGED, old.sendRawKey(HidKey(4)))
        assertEquals(0, f.driver.reports)
        runCurrent(); assertNotSame(old, f.host.keyboardConnection.value)
        assertTrue(f.host.keyboardConnection.value!!.isConnected)
    }
    @Test fun invalidationDuringKeyPressStillReleasesKey() = test { f ->
        f.connected()
        val session = f.host.keyboardConnection.value!!
        val sending = async { session.sendRawKey(HidKey(4)) }
        runCurrent()
        assertEquals(1, f.driver.reports)
        f.driver.callback.connection(device, HidDeviceConnection.DISCONNECTED)
        advanceTimeBy(200); runCurrent()
        assertEquals(HidKeyResult.CONNECTION_CHANGED, sending.await())
        assertEquals(2, f.driver.reports)
    }
    @Test fun registrationLossImmediatelyRevokesSession() = test { f ->
        f.connected(); val old = f.host.keyboardConnection.value!!
        f.driver.callback.registered(false)
        assertFalse(old.isConnected)
        runCurrent(); assertEquals(2, f.requests.size)
    }
    @Test fun missingProfileCallbackTimesOutAndLateProxyCloses() = test { f ->
        f.start(); val old = f.requests.single()
        advanceTimeBy(1000); runCurrent()
        assertEquals(HidState.Failed(HidFailure.PROFILE), f.host.state.value)
        f.host.retry(); runCurrent(); assertEquals(2, f.requests.size)
        old.deliver(f.handle)
        assertEquals(1, f.driver.closes); assertEquals(0, f.driver.registrations)
    }
    @Test fun registrationTimeoutWaitsForConfirmedCleanup() = test { f ->
        f.driver.ackRelease = false; f.open()
        advanceTimeBy(1000); runCurrent()
        assertEquals(1, f.driver.unregisters); assertEquals(0, f.driver.closes)
        f.driver.callback.registered(true); runCurrent()
        assertSame(HidState.Preparing, f.host.state.value)
        advanceTimeBy(1000); runCurrent()
        assertEquals(HidState.Failed(HidFailure.CLEANUP), f.host.state.value)
        f.host.retry(); runCurrent()
        assertEquals(1, f.requests.size); assertEquals(1, f.driver.unregisters)
        f.driver.callback.registered(false); runCurrent()
        assertEquals(1, f.driver.closes)
        assertEquals(HidState.Failed(HidFailure.REGISTRATION), f.host.state.value)
        f.host.retry(); runCurrent(); assertEquals(2, f.requests.size)
    }
    @Test fun rejectedRegistrationCanRetryWithFreshHandle() = test { f ->
        f.driver.acceptRegistration = false; f.open()
        assertEquals(HidState.Failed(HidFailure.REGISTRATION), f.host.state.value)
        assertEquals(1, f.driver.closes); assertEquals(0, f.driver.unregisters)
        f.host.retry(); runCurrent(); assertEquals(2, f.requests.size)
    }
    @Test fun failedRecoveryDoesNotLoop() = test { f ->
        f.ready(); f.driver.callback.registered(false); runCurrent()
        val replacement = f.newHandle(); f.requests.last().deliver(replacement.first); runCurrent()
        replacement.second.callback.registered(false); runCurrent()
        f.host.refresh(); runCurrent()
        assertEquals(2, f.requests.size)
        assertEquals(HidState.Failed(HidFailure.REGISTRATION), f.host.state.value)
    }
    @Test fun backgroundLossRecoversOnlyOnResume() = test { f ->
        f.ready(); f.host.background(f.client); runCurrent()
        f.driver.callback.registered(false); runCurrent(); assertEquals(1, f.requests.size)
        f.host.foreground(f.client); runCurrent(); assertEquals(2, f.requests.size)
    }
    @Test fun oldCallbacksCannotInvalidateReplacement() = test { f ->
        f.connected(); val old = f.driver.callback
        old.registered(false); runCurrent()
        val (handle, driver) = f.newHandle(); f.requests.last().deliver(handle); runCurrent()
        driver.callback.registered(true); driver.callback.connection(device, HidDeviceConnection.CONNECTED); runCurrent()
        val session = f.host.keyboardConnection.value!!
        old.registered(false); old.connection(device, HidDeviceConnection.DISCONNECTED)
        f.requests.first().serviceLost(); runCurrent()
        assertSame(session, f.host.keyboardConnection.value); assertTrue(session.isConnected)
    }
    @Test fun permissionGrantStartsWithoutActivityRecreation() = test { f ->
        f.environment = HidEnvironment.PermissionRequired; f.start()
        assertSame(HidState.NoPermission, f.host.state.value); assertTrue(f.requests.isEmpty())
        f.environment = HidEnvironment.Available(listOf(device)); f.host.retry(); runCurrent()
        assertEquals(1, f.requests.size)
    }
    @Test fun permissionLossCancelsPendingAcquisition() = test { f ->
        f.start(); f.environment = HidEnvironment.PermissionRequired; f.host.refresh(); runCurrent()
        f.requests.single().deliver(f.handle)
        assertEquals(1, f.driver.closes); assertEquals(0, f.driver.registrations)
    }
    @Test fun radioLossAllowsRecoveryWithoutUnregisterAcknowledgement() = test { f ->
        f.connected(); f.driver.ackRelease = false
        f.environment = HidEnvironment.BluetoothOff; f.host.refresh(); runCurrent()
        assertEquals(1, f.driver.closes); assertNull(f.host.keyboardConnection.value)
        f.environment = HidEnvironment.Available(listOf(device)); f.host.refresh(); runCurrent()
        assertEquals(2, f.requests.size)
    }
    @Test fun serviceLossImmediatelyRevokesAndReacquires() = test { f ->
        f.connected(); val old = f.host.keyboardConnection.value!!
        f.requests.single().serviceLost(); assertFalse(old.isConnected); runCurrent()
        assertEquals(2, f.requests.size); assertEquals(1, f.driver.closes)
    }
    @Test fun rejectedConnectAllowsRetry() = test { f ->
        f.ready(); f.driver.acceptConnect = false; f.host.connectDevice(device.address); runCurrent()
        assertEquals(HidFailure.CONNECTION, (f.host.state.value as HidState.NoBond).failure)
        f.driver.acceptConnect = true; f.host.connectDevice(device.address); runCurrent()
        f.driver.callback.connection(device, HidDeviceConnection.CONNECTED); runCurrent()
        assertEquals(HidState.Done(device), f.host.state.value)
    }
    @Test fun connectionCallbackBeforeRegistrationIsPreserved() = test { f ->
        f.open(); f.driver.callback.connection(device, HidDeviceConnection.CONNECTED)
        f.driver.callback.registered(true); runCurrent()
        assertEquals(HidState.Done(device), f.host.state.value)
    }
    @Test fun staleConnectionQueryCannotResurrectDisconnectedSession() = test { f ->
        f.open(); f.driver.query = {
            f.driver.callback.connection(device, HidDeviceConnection.DISCONNECTED)
            listOf(device)
        }
        f.driver.callback.registered(true); runCurrent()
        assertNull(f.host.keyboardConnection.value)
    }
    @Test fun closeKeepsOwnershipUntilReleaseConfirmed() = test { f ->
        f.connected(); f.driver.ackRelease = false
        f.host.close(); runCurrent(); assertEquals(0, f.driver.closes)
        assertNull(f.host.keyboardConnection.value)
        f.driver.callback.registered(false); runCurrent(); assertEquals(1, f.driver.closes)
    }
    @Test fun cancellationAfterDeliveryClosesUnclaimedHandle() = test { f ->
        f.start(); f.requests.single().deliver(f.handle); f.requests.single().cancel()
        runCurrent(); assertEquals(1, f.driver.closes); assertEquals(0, f.driver.registrations)
    }
    @Test fun duplicateDeliveryDoesNotCloseClaimedHandle() = test { f ->
        f.ready(); f.requests.single().deliver(f.handle)
        assertEquals(0, f.driver.closes)
    }

    @Test fun missingConnectionCallbackTimesOutAndAllowsFreshRetry() = test { f ->
        f.ready()
        f.host.connectDevice(device.address); runCurrent()
        advanceTimeBy(1000); runCurrent()
        assertEquals(HidState.Failed(HidFailure.CONNECTION), f.host.state.value)
        assertEquals(1, f.driver.unregisters)
        assertEquals(1, f.driver.closes)
        f.host.retry(); runCurrent()
        val (handle, driver) = f.newHandle()
        f.requests.last().deliver(handle); runCurrent()
        driver.callback.registered(true); runCurrent()
        f.host.connectDevice(device.address); runCurrent()
        driver.callback.connection(device, HidDeviceConnection.CONNECTED); runCurrent()
        assertEquals(1, driver.connects)
        assertEquals(HidState.Done(device), f.host.state.value)
    }
    @Test fun repeatedConnectingCallbacksAndResumeDoNotExtendDeadline() = test { f ->
        f.ready(); f.host.connectDevice(device.address); runCurrent()
        advanceTimeBy(900)
        f.driver.callback.connection(device, HidDeviceConnection.CONNECTING)
        f.host.foreground(f.client); runCurrent()
        advanceTimeBy(100); runCurrent()
        assertEquals(HidState.Failed(HidFailure.CONNECTION), f.host.state.value)
    }
    @Test fun successfulConnectionCancelsItsDeadline() = test { f ->
        f.ready(); f.host.connectDevice(device.address); runCurrent()
        advanceTimeBy(900)
        f.driver.callback.connection(device, HidDeviceConnection.CONNECTED); runCurrent()
        advanceTimeBy(2000); runCurrent()
        assertEquals(HidState.Done(device), f.host.state.value)
        assertEquals(0, f.driver.unregisters)
    }
    @Test fun cancelledConnectionIgnoresLateSuccessDuringCleanup() = test { f ->
        f.ready(); f.driver.ackRelease = false
        f.host.connectDevice(device.address); runCurrent()
        f.host.cancelConnectionAttempt(); runCurrent()
        f.driver.callback.connection(device, HidDeviceConnection.CONNECTED); runCurrent()
        assertNull(f.host.keyboardConnection.value)
        assertSame(HidState.Preparing, f.host.state.value)
        f.host.retry(); runCurrent()
        assertEquals(1, f.requests.size)
        assertEquals(1, f.driver.unregisters)
        f.driver.callback.registered(false); runCurrent()
        assertEquals(2, f.requests.size)
    }
    @Test fun timeoutRetainsOwnershipWhenCleanupCallbackIsMissing() = test { f ->
        f.ready(); f.driver.ackRelease = false
        f.host.connectDevice(device.address); runCurrent()
        advanceTimeBy(2000); runCurrent()
        assertEquals(HidState.Failed(HidFailure.CLEANUP), f.host.state.value)
        f.driver.callback.connection(device, HidDeviceConnection.CONNECTED); runCurrent()
        assertNull(f.host.keyboardConnection.value)
        assertEquals(1, f.requests.size)
        assertEquals(0, f.driver.closes)
    }
    @Test fun lateCallbackFromCancelledAttemptCannotChangeNewConnection() = test { f ->
        f.ready(); f.host.connectDevice(device.address); runCurrent()
        f.host.cancelConnectionAttempt(); runCurrent()
        val (handle, driver) = f.newHandle()
        f.requests.last().deliver(handle); runCurrent()
        driver.callback.registered(true)
        driver.callback.connection(device, HidDeviceConnection.CONNECTED); runCurrent()
        val session = f.host.keyboardConnection.value
        f.driver.callback.connection(device, HidDeviceConnection.CONNECTED)
        f.driver.callback.connection(device, HidDeviceConnection.DISCONNECTED); runCurrent()
        assertNotNull(session)
        assertSame(session, f.host.keyboardConnection.value)
        assertTrue(session!!.isConnected)
    }
    @Test fun completedConnectionWinsOverStaleCancellation() = test { f ->
        f.ready(); f.host.connectDevice(device.address); runCurrent()
        val attempt = (f.handle.state.value as HidProfileState.Ready).link as HidLink.Connecting
        f.driver.callback.connection(device, HidDeviceConnection.CONNECTED)
        assertFalse(f.handle.cancelConnection(attempt))
        runCurrent()
        assertEquals(HidState.Done(device), f.host.state.value)
    }
    @Test fun callbackInitiatedConnectionAlsoHasDeadline() = test { f ->
        f.ready()
        f.driver.callback.connection(device, HidDeviceConnection.CONNECTING); runCurrent()
        advanceTimeBy(1000); runCurrent()
        assertEquals(HidState.Failed(HidFailure.CONNECTION), f.host.state.value)
    }

    private fun test(block: suspend TestScope.(Fixture) -> Unit) = runTest {
        val f = Fixture(testScheduler)
        try { block(f) } finally {
            f.handles.forEach { it.serviceLost() }
            f.host.close(); runCurrent()
        }
    }
    private class Fixture(val scheduler: TestCoroutineScheduler) : HidPlatform {
        val dispatcher = StandardTestDispatcher(scheduler)
        val client = Any()
        var environment: HidEnvironment = HidEnvironment.Available(listOf(device))
        val requests = mutableListOf<HidProfileAcquisition>()
        val handles = mutableListOf<HidProfileHandle>()
        val driver = Driver()
        val handle = HidProfileHandle(driver, dispatcher, RawHidKeyboard()).also { handles += it }
        val host = HidConnectionHost(dispatcher, dispatcher, this, timeoutMillis = 1000)
        override fun environment() = environment
        override fun observe(changed: () -> Unit) = AutoCloseable { }
        override fun openProfile(delivered: (HidProfileAcquisition, HidProfileHandle) -> Unit) =
            HidProfileAcquisition(delivered, dispose = { it.close() }).also { requests += it }
        fun newHandle(): Pair<HidProfileHandle, Driver> {
            val driver = Driver()
            return HidProfileHandle(driver, dispatcher, RawHidKeyboard()).also { handles += it } to driver
        }
        fun start() { host.foreground(client); scheduler.runCurrent() }
        fun open() { start(); requests.single().deliver(handle); scheduler.runCurrent() }
        fun ready() { open(); driver.callback.registered(true); scheduler.runCurrent() }
        fun connected() { ready(); driver.callback.connection(device, HidDeviceConnection.CONNECTED); scheduler.runCurrent() }
    }
    private class Driver : HidProfileDriver {
        lateinit var callback: HidRegistrationCallback
        var registrations = 0
        var unregisters = 0
        var closes = 0
        var connects = 0
        var reports = 0
        var acceptRegistration = true
        var acceptConnect = true
        var ackRelease = true
        var query: () -> List<HidDevice> = { emptyList() }
        override fun register(callback: HidRegistrationCallback): Boolean {
            registrations++; this.callback = callback; return acceptRegistration
        }
        override fun unregister(): Boolean {
            unregisters++; if (ackRelease) callback.registered(false); return true
        }
        override fun connectedDevices() = query()
        override fun connect(device: HidDevice): Boolean { connects++; return acceptConnect }
        override fun disconnect(device: HidDevice) = true
        override fun report(device: HidDevice, bytes: ByteArray): Boolean { reports++; return true }
        override fun close() { closes++ }
    }
    companion object { private val device = HidDevice("Computer", "00:11:22:33:44:55") }
}
