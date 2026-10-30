package com.flexkeys

import com.flexkeys.settings.SettingsActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Opens the real Settings screen and every page in it. A crash on opening (like building the home list before the
 * preview keyboard existed) fails the build here instead of reaching the phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsSmokeTest {

    @Test fun everySettingsPageOpens() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create().start().resume().visible()
        val activity = controller.get()
        val pages = activity.topicIds()
        assertTrue("expected the settings pages", pages.size >= 8)
        for (id in pages) {
            activity.openTopicForTest(id)
            activity.openHomeForTest()
        }
        controller.pause().stop().destroy()
    }
}
