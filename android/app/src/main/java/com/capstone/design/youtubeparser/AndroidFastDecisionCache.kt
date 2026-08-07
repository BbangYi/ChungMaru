package com.capstone.design.youtubeparser

internal class AndroidFastDecisionCache(
    private val ttlMs: Long,
    private val maxSize: Int
) {
    private val fingerprints = LinkedHashMap<String, Long>()

    fun isRecent(fingerprint: String, nowMs: Long, hasActiveMasks: Boolean): Boolean {
        if (!hasActiveMasks) return false
        prune(nowMs)
        val cachedAtMs = fingerprints[fingerprint] ?: return false
        return nowMs - cachedAtMs in 0L..ttlMs
    }

    fun remember(fingerprint: String, nowMs: Long) {
        prune(nowMs)
        fingerprints[fingerprint] = nowMs
        while (fingerprints.size > maxSize) {
            val oldest = fingerprints.keys.firstOrNull() ?: break
            fingerprints.remove(oldest)
        }
    }

    fun clear() {
        fingerprints.clear()
    }

    fun size(): Int {
        return fingerprints.size
    }

    private fun prune(nowMs: Long) {
        val iterator = fingerprints.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMs - entry.value > ttlMs) {
                iterator.remove()
            }
        }
    }
}
