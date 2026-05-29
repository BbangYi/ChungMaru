package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityCharacterBoxPolicyTest {

    @Test
    fun shouldRequest_forActionableText() {
        assertTrue(
            AccessibilityCharacterBoxPolicy.shouldRequest(
                rawText = "What is Tlqkf?",
                displayText = "What is Tlqkf?",
                className = "android.widget.TextView",
                viewIdResourceName = null
            )
        )
    }

    @Test
    fun requestRanges_forActionableTextTargetsOnlyMatchedSpan() {
        val text = "This is a long result snippet before 시발 and after clean words."

        val ranges = AccessibilityCharacterBoxPolicy.requestRanges(
            rawText = text,
            displayText = text,
            className = "android.widget.TextView",
            viewIdResourceName = null
        )

        assertEquals(
            listOf(
                AccessibilityCharacterBoxPolicy.RequestRange(
                    start = text.indexOf("시발"),
                    length = "시발".length
                )
            ),
            ranges
        )
    }

    @Test
    fun shouldRequest_forInputLikeText() {
        assertTrue(
            AccessibilityCharacterBoxPolicy.shouldRequest(
                rawText = "ordinary query",
                displayText = "ordinary query",
                className = "android.widget.EditText",
                viewIdResourceName = "com.google.android.youtube:id/search_edit_text"
            )
        )
    }

    @Test
    fun requestRanges_forInputLikeCleanTextUsesFullInputRange() {
        val text = "ordinary query"

        val ranges = AccessibilityCharacterBoxPolicy.requestRanges(
            rawText = text,
            displayText = text,
            className = "android.widget.EditText",
            viewIdResourceName = "com.google.android.youtube:id/search_edit_text"
        )

        assertEquals(
            listOf(AccessibilityCharacterBoxPolicy.RequestRange(start = 0, length = text.length)),
            ranges
        )
    }

    @Test
    fun shouldSkip_forCleanNonInputText() {
        assertFalse(
            AccessibilityCharacterBoxPolicy.shouldRequest(
                rawText = "Contemporary Korean Slang",
                displayText = "Contemporary Korean Slang",
                className = "android.widget.TextView",
                viewIdResourceName = null
            )
        )
    }

    @Test
    fun shouldRequest_allowsTrimmedRawTextWhenDisplayTextIsTrimmed() {
        assertTrue(
            AccessibilityCharacterBoxPolicy.shouldRequest(
                rawText = "  시발  ",
                displayText = "시발",
                className = "android.widget.TextView",
                viewIdResourceName = null
            )
        )
    }

    @Test
    fun requestRanges_skipsCleanNonInputText() {
        val ranges = AccessibilityCharacterBoxPolicy.requestRanges(
            rawText = "Contemporary Korean Slang",
            displayText = "Contemporary Korean Slang",
            className = "android.widget.TextView",
            viewIdResourceName = null
        )

        assertTrue(ranges.isEmpty())
    }

    @Test
    fun shouldSkip_whenDisplayedTextCameFromContentDescription() {
        assertFalse(
            AccessibilityCharacterBoxPolicy.shouldRequest(
                rawText = "clean raw text",
                displayText = "image description",
                className = "android.view.View",
                viewIdResourceName = null
            )
        )
    }
}
