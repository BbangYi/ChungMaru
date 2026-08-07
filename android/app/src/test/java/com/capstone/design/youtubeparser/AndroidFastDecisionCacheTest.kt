package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidFastDecisionCacheTest {

    @Test
    fun isRecent_requiresActiveMasks() {
        val cache = AndroidFastDecisionCache(ttlMs = 350L, maxSize = 8)
        cache.remember("text|10,20,30,40", nowMs = 1_000L)

        assertFalse(cache.isRecent("text|10,20,30,40", nowMs = 1_050L, hasActiveMasks = false))
        assertTrue(cache.isRecent("text|10,20,30,40", nowMs = 1_050L, hasActiveMasks = true))
    }

    @Test
    fun isRecent_expiresAfterTtl() {
        val cache = AndroidFastDecisionCache(ttlMs = 350L, maxSize = 8)
        cache.remember("text|10,20,30,40", nowMs = 1_000L)

        assertTrue(cache.isRecent("text|10,20,30,40", nowMs = 1_350L, hasActiveMasks = true))
        assertFalse(cache.isRecent("text|10,20,30,40", nowMs = 1_351L, hasActiveMasks = true))
    }

    @Test
    fun remember_capsOldestEntries() {
        val cache = AndroidFastDecisionCache(ttlMs = 10_000L, maxSize = 2)
        cache.remember("a", nowMs = 1_000L)
        cache.remember("b", nowMs = 1_001L)
        cache.remember("c", nowMs = 1_002L)

        assertEquals(2, cache.size())
        assertFalse(cache.isRecent("a", nowMs = 1_003L, hasActiveMasks = true))
        assertTrue(cache.isRecent("b", nowMs = 1_003L, hasActiveMasks = true))
        assertTrue(cache.isRecent("c", nowMs = 1_003L, hasActiveMasks = true))
    }

    @Test
    fun clear_removesEntries() {
        val cache = AndroidFastDecisionCache(ttlMs = 350L, maxSize = 8)
        cache.remember("text|10,20,30,40", nowMs = 1_000L)

        cache.clear()

        assertEquals(0, cache.size())
        assertFalse(cache.isRecent("text|10,20,30,40", nowMs = 1_050L, hasActiveMasks = true))
    }
}
