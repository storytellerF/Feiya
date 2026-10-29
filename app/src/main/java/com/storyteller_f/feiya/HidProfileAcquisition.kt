package com.storyteller_f.feiya
import kotlinx.coroutines.flow.MutableStateFlow

/** Owns an unregistered proxy until claimed, including delivery after cancellation. */
class HidProfileAcquisition(
    private val delivered: (HidProfileAcquisition, HidProfileHandle) -> Unit,
    private val dispose: (HidProfileHandle) -> Unit,
    private val cancelled: (HidProfileAcquisition) -> Unit = {},
) {
    private sealed interface State {
        data object Waiting : State
        class Delivered(val handle: HidProfileHandle) : State
        class Claimed(val handle: HidProfileHandle) : State
        data object Cancelled : State
    }
    private val state = MutableStateFlow<State>(State.Waiting)
    fun deliver(handle: HidProfileHandle) {
        if (state.compareAndSet(State.Waiting, State.Delivered(handle))) delivered(this, handle)
        else if (handle()?.platformIdentity !== handle.platformIdentity) dispose(handle)
    }
    fun claim(handle: HidProfileHandle): Boolean {
        val current = state.value as? State.Delivered ?: return false
        return current.handle === handle && state.compareAndSet(current, State.Claimed(handle))
    }
    fun cancel() {
        while (true) {
            val current = state.value
            if (current is State.Claimed || current == State.Cancelled) return
            if (state.compareAndSet(current, State.Cancelled)) {
                cancelled(this)
                if (current is State.Delivered) dispose(current.handle)
                return
            }
        }
    }
    private fun handle() = when (val current = state.value) {
        is State.Delivered -> current.handle
        is State.Claimed -> current.handle
        else -> null
    }
    fun serviceLost() { handle()?.serviceLost() }
    fun invalidate() { handle()?.invalidate() }
}
