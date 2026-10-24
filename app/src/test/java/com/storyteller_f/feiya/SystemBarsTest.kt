package com.storyteller_f.feiya

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import com.storyteller_f.feiya.ui.theme.AppTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SystemBarsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun systemBarIconsFollowAppThemeChanges() {
        val dark = mutableStateOf(false)
        compose.setContent { AppTheme(darkTheme = dark.value, dynamicColor = false) {} }

        compose.runOnIdle {
            val window = compose.activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            assertTrue(controller.isAppearanceLightStatusBars)
            assertTrue(controller.isAppearanceLightNavigationBars)
            dark.value = true
        }
        compose.runOnIdle {
            val window = compose.activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            assertFalse(controller.isAppearanceLightStatusBars)
            assertFalse(controller.isAppearanceLightNavigationBars)
        }
    }
}
