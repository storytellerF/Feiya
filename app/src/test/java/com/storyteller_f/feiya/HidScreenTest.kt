package com.storyteller_f.feiya

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.junit4.createComposeRule
import com.storyteller_f.feiya.ui.components.HidScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "en")
class HidScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editsStayImmediateAcrossTaskUpdatesAndReconnect() {
        val connection = mutableStateOf<HidState>(HidState.Done(HidDevice("computer", "address")))
        val keyboard = mutableStateOf(HidKeyboardState())
        val sent = mutableListOf<String>()
        compose.setContent {
            MaterialTheme { HidScreen(connection.value, keyboardState = keyboard.value, sendText = { sent.add(it) }) }
        }
        compose.onNodeWithTag("hid_text").performScrollTo().performTextInput("first")
        compose.runOnIdle {
            keyboard.value = keyboard.value.copy(tasks = listOf(HidSendTask(1, HidTaskKind.TEXT, "computer", 5, status = HidTaskStatus.SENDING)))
        }
        compose.onNodeWithTag("hid_text").performScrollTo().performTextInput(" second")
        compose.onNodeWithTag("hid_text").performTextInputSelection(TextRange(5))
        compose.runOnIdle { keyboard.value = keyboard.value.copy(layout = TargetKeyboardLayout.DVORAK) }
        compose.onNodeWithTag("hid_text").performTextInput("!")
        compose.onNodeWithTag("hid_text").assertTextContains("first! second")
        compose.onNodeWithTag("hid_send").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("first! second"), sent); connection.value = HidState.BluetoothOff }
        compose.onNodeWithTag("hid_tasks").assertExists()
        compose.runOnIdle { connection.value = HidState.Done(HidDevice("other", "address2")) }
        compose.onNodeWithTag("hid_text").performScrollTo().assertTextContains("first! second")
    }

    @Test fun disconnectedTaskRemainsVisibleAndCancelTargetsOneTask() {
        val tasks = listOf(HidSendTask(42, HidTaskKind.TEXT, "computer", 8, sentKeys = 2, status = HidTaskStatus.SENDING))
        val cancelled = mutableListOf<Long>()
        compose.setContent {
            MaterialTheme { HidScreen(HidState.BluetoothOff, keyboardState = HidKeyboardState(tasks = tasks), cancelTask = { cancelled.add(it) }) }
        }
        compose.onNodeWithText("Sending · 2/8 keys").assertExists()
        compose.onNodeWithTag("hid_cancel_42").performClick()
        compose.runOnIdle { assertEquals(listOf(42L), cancelled) }
    }

    @Test fun registrationFailureShowsRetryAndPreservesDraft() {
        val connection = mutableStateOf<HidState>(HidState.Done(HidDevice("computer", "address")))
        var retries = 0
        compose.setContent {
            MaterialTheme { HidScreen(connection.value, retryConnection = { retries++ }) }
        }
        compose.onNodeWithTag("hid_text").performScrollTo().performTextInput("Keep this draft")
        compose.runOnIdle { connection.value = HidState.Failed(HidFailure.REGISTRATION) }
        compose.onNodeWithText("Could not register the Bluetooth keyboard. Close other Bluetooth keyboard apps and try again.").assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            connection.value = HidState.Done(HidDevice("computer", "address"))
        }
        compose.onNodeWithTag("hid_text").performScrollTo().assertTextContains("Keep this draft")
    }
    @Test fun connectingDialogOffersCancellation() {
        val device = HidDevice("Computer", "00:11:22:33:44:55")
        var cancellations = 0
        val state = mutableStateOf<HidState>(HidState.NoBond(listOf(device), connecting = device.address))
        compose.setContent {
            MaterialTheme { HidScreen(state.value, cancelConnection = {
                cancellations++
                state.value = HidState.Preparing
            }) }
        }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(1, cancellations) }
        compose.onNodeWithText("connecting to ${device.address}").assertDoesNotExist()
    }

}
