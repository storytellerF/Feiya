package com.storyteller_f.feiya

import android.content.Intent
import com.storyteller_f.feiya.service.AppService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FeiyaApplication::class)
class HidNotificationTest {
    @Test fun notificationLaunchesMainActivityWithExistingInstanceReuse() {
        val controller = Robolectric.buildService(AppService::class.java)
        controller.get().stop()
        try {
            controller.create()
            val notification = shadowOf(controller.get()).lastForegroundNotification
            val pending = notification.contentIntent
            pending.send()
            val launched = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertEquals(MainActivity::class.java.name, launched.component!!.className)
            assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                launched.flags and (Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            assertEquals(0, launched.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
            assertTrue(pending.isImmutable)
        } finally {
            controller.destroy()
        }
    }
}
