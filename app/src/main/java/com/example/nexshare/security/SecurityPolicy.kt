package com.example.nexshare.security

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Receiver-side checks run before any bytes are written.
 * Every rule here is enforced regardless of what the UI shows.
 */
class SecurityPolicy(
    private val maxRequestsPerMinute: Int = 6,
    private val blockedExtensions: Set<String> = DEFAULT_BLOCKED,
    private val maxFileBytes: Long = 4L * 1024 * 1024 * 1024
) {

    private val blockedDevices = ConcurrentHashMap.newKeySet<String>()
    private val overrideExtensions = ConcurrentHashMap.newKeySet<String>()
    private val recentRequests = ConcurrentHashMap<String, MutableList<Long>>()

    fun block(peerKey: String) { blockedDevices.add(peerKey) }
    fun unblock(peerKey: String) { blockedDevices.remove(peerKey) }
    fun isBlocked(peerKey: String): Boolean = blockedDevices.contains(peerKey)

    /** Explicit user confirmation for one blocked extension. Never set automatically. */
    fun allowExtension(ext: String) { overrideExtensions.add(ext.lowercase()) }

    /**
     * Returns a rejection reason, or null if the offer passes every check.
     * Called before the approval dialog, so blocked peers never reach the user.
     */
    fun checkOffer(peerKey: String, offer: JSONObject): String? {
        if (isBlocked(peerKey)) return "Sender is blocked"
        if (!allowRequest(peerKey)) return "Too many requests, try again later"

        val name = sanitizeName(offer.optString("fileName"))
        val size = offer.optLong("fileSize", -1L)
        val id = offer.optString("transferId")
        val hash = offer.optString("sha256")

        return when {
            id.isEmpty() || id.length > 64 || !id.all { it.isLetterOrDigit() || it == '-' } ->
                "Invalid transfer id"
            size <= 0L || size > maxFileBytes -> "File size not allowed"
            hash.length != 64 || !hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } ->
                "Invalid hash"
            isBlockedExtension(name) -> "File type blocked"
            else -> null
        }
    }

    /** True when the extension is blocked and the user has not overridden it. */
    fun isBlockedExtension(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return false
        return ".$ext" in blockedExtensions && ext !in overrideExtensions
    }

    fun blockedSnapshot(): List<String> = blockedDevices.toList().sorted()

    /** Sliding one-minute window per peer. */
    private fun allowRequest(peerKey: String): Boolean {
        val now = System.currentTimeMillis()
        val window = recentRequests.getOrPut(peerKey) { mutableListOf() }
        synchronized(window) {
            window.removeAll { now - it > 60_000L }
            if (window.size >= maxRequestsPerMinute) return false
            window.add(now)
            return true
        }
    }

    /** Strips path parts and control characters so the name can be shown safely. */
    fun sanitizeName(raw: String): String =
        raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001F]"), "")
            .take(255)

    companion object {
        val DEFAULT_BLOCKED = setOf(".apk", ".exe", ".bat", ".cmd", ".sh", ".ps1", ".scr")
    }
}