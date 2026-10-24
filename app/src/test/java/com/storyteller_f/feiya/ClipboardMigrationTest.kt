package com.storyteller_f.feiya

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.storyteller_f.feiya.service.Message
import com.storyteller_f.feiya.ui.components.MessageItem
import com.storyteller_f.feiya.ui.components.QrCodeContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "en")
class ClipboardMigrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun copyMessagePreservesMarkdownAndUnicode() {
        val text = "**Hello**\n你好 👋"
        compose.setContent { MaterialTheme { MessageItem(Message("sender", text)) } }

        compose.onNodeWithText("sender").performClick()
        compose.onNodeWithText("Copy").performClick()
        compose.waitForIdle()

        compose.runOnIdle {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(text, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }

    @Test fun copyQrLinkUsesDisplayedUrl() {
        val url = "http://192.168.1.2:8080/shares/42"
        val state = QrCodeState(
            url = url,
            image = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
        )
        compose.setContent { MaterialTheme { QrCodeContent(state, reload = {}, selectAddress = {}) } }

        compose.onNodeWithText("copy link🔗").performClick()
        compose.waitForIdle()

        compose.runOnIdle {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(url, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }
}
