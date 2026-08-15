package com.smsexpense.tracker.data.local.sms

import android.content.Context
import android.provider.Telephony
import com.smsexpense.tracker.domain.model.IncomingMessage
import com.smsexpense.tracker.domain.source.DeviceSmsSource
import com.smsexpense.tracker.domain.source.StoredSms
import com.smsexpense.tracker.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the device SMS inbox via the Telephony content provider. Requires the
 * READ_SMS runtime permission — callers must check it first; without it the
 * queries throw SecurityException which we surface as an empty result plus a log.
 *
 * Note: Android has NO system "pick an SMS" intent (unlike contacts), so an
 * in-app picker over this provider is the only supported approach.
 */
class ContentResolverSmsSource(private val context: Context) : DeviceSmsSource {

    override suspend fun recentMessages(limit: Int): List<StoredSms> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StoredSms>()
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT $limit",
            )?.use { cursor ->
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIdx) ?: continue
                    result.add(
                        StoredSms(
                            sender = address,
                            body = cursor.getString(bodyIdx) ?: "",
                            timestamp = cursor.getLong(dateIdx),
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            AppLog.w("READ_SMS not granted while reading inbox", e)
        } catch (e: Exception) {
            AppLog.e("Failed to read SMS inbox", e)
        }
        result
    }

    override suspend fun messagesBetween(from: Long, to: Long): List<IncomingMessage> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<IncomingMessage>()
            try {
                context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?",
                    arrayOf(from.toString(), to.toString()),
                    "${Telephony.Sms.DATE} ASC",
                )?.use { cursor ->
                    val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    while (cursor.moveToNext()) {
                        val address = cursor.getString(addressIdx) ?: continue
                        val body = cursor.getString(bodyIdx) ?: continue
                        if (body.isBlank()) continue
                        result.add(
                            IncomingMessage(
                                sender = address,
                                body = body,
                                timestamp = cursor.getLong(dateIdx),
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                AppLog.w("READ_SMS not granted while scanning inbox", e)
            } catch (e: Exception) {
                AppLog.e("Failed to scan SMS inbox", e)
            }
            result
        }
}
