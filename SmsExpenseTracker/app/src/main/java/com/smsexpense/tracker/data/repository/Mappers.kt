package com.smsexpense.tracker.data.repository

import com.smsexpense.tracker.data.local.entity.CategoryEntity
import com.smsexpense.tracker.data.local.entity.ImportHistoryEntity
import com.smsexpense.tracker.data.local.entity.PaymentEntity
import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.model.ImportRecord
import com.smsexpense.tracker.domain.model.Payment
import com.smsexpense.tracker.domain.model.PaymentSource
import com.smsexpense.tracker.domain.model.PaymentStatus
import com.smsexpense.tracker.domain.model.SyncStatus

fun PaymentEntity.toDomain() = Payment(
    id = id,
    amount = amount,
    currency = currency,
    merchant = merchant,
    categoryId = categoryId,
    sender = sender,
    originalMessage = originalMessage,
    timestamp = timestamp,
    status = runCatching { PaymentStatus.valueOf(status) }.getOrDefault(PaymentStatus.UNCATEGORIZED),
    syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.PENDING),
    confidence = confidence,
    createdAt = createdAt,
    source = runCatching { PaymentSource.valueOf(source) }.getOrDefault(PaymentSource.SMS_REALTIME),
)

fun ImportHistoryEntity.toDomain() = ImportRecord(
    id = id,
    importedAt = importedAt,
    fromDate = fromDate,
    toDate = toDate,
    transactionCount = transactionCount,
    totalAmount = totalAmount,
    currency = currency,
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    icon = icon,
    color = color,
    sortOrder = sortOrder,
    createdAt = createdAt,
)
