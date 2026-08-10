package com.smsexpense.tracker.domain.source

import com.smsexpense.tracker.domain.model.IncomingMessage

/** A stored SMS shown in the sender-picker UI. */
data class StoredSms(
    val sender: String,
    val body: String,
    val timestamp: Long,
)

/**
 * Read-only access to SMS already stored on the device (inbox). Used by the
 * sender picker and the historical import. Kept as an interface so tests (and a
 * future non-SMS source) can substitute a fake — the import pipeline never talks
 * to the ContentResolver directly.
 */
interface DeviceSmsSource {
    /** Recent inbox messages, newest first, for the sender-picker UI. */
    suspend fun recentMessages(limit: Int): List<StoredSms>

    /** Inbox messages whose received-at date falls inside [from, to]. */
    suspend fun messagesBetween(from: Long, to: Long): List<IncomingMessage>
}
