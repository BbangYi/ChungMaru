package com.capstone.design.youtubeparser

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualTextCaptureSupportTest {
    private companion object {
        private const val CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT = 1
        private const val CAPABILITY_CAN_TAKE_SCREENSHOT_API_30 = 1 shl 7
    }

    @Test
    fun inspect_rejectsDevicesBelowAndroid11() {
        val state = VisualTextCaptureSupport.inspect(
            sdkInt = Build.VERSION_CODES.Q,
            capabilities = CAPABILITY_CAN_TAKE_SCREENSHOT_API_30
        )

        assertFalse(state.supported)
        assertFalse(state.hasScreenshotCapability)
        assertEquals(VisualTextCaptureSupport.REASON_API_BELOW_30, state.reason)
    }

    @Test
    fun inspect_acceptsAndroid11PlusWithScreenshotCapability() {
        val state = VisualTextCaptureSupport.inspect(
            sdkInt = Build.VERSION_CODES.R,
            capabilities = CAPABILITY_CAN_TAKE_SCREENSHOT_API_30
        )

        assertTrue(state.supported)
        assertTrue(state.hasScreenshotCapability)
        assertEquals(VisualTextCaptureSupport.REASON_READY, state.reason)
    }

    @Test
    fun inspect_rejectsAndroid11PlusWithoutScreenshotCapability() {
        val state = VisualTextCaptureSupport.inspect(
            sdkInt = Build.VERSION_CODES.R,
            capabilities = CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT
        )

        assertFalse(state.supported)
        assertFalse(state.hasScreenshotCapability)
        assertEquals(VisualTextCaptureSupport.REASON_SCREENSHOT_CAPABILITY_MISSING, state.reason)
    }
}
