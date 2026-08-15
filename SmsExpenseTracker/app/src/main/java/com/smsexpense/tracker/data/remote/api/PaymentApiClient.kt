package com.smsexpense.tracker.data.remote.api

import com.smsexpense.tracker.data.remote.dto.PaymentDto

sealed class ApiResult {
    data object Success : ApiResult()
    /** [retryable] distinguishes network/5xx (retry later) from 4xx (won't succeed as-is). */
    data class Failure(val message: String, val retryable: Boolean) : ApiResult()
    data object Disabled : ApiResult()
}

/**
 * Abstraction over the backend. Base URL, auth and headers are all supplied by
 * [ApiConfigProvider], so swapping servers or auth schemes never touches callers.
 */
interface PaymentApiClient {
    suspend fun postPayment(payment: PaymentDto): ApiResult
}

/** Where the client reads its configuration each call (backed by Settings). */
fun interface ApiConfigProvider {
    suspend fun current(): ApiConfig
}

data class ApiConfig(
    val enabled: Boolean,
    val baseUrl: String,
    val authToken: String,
    val extraHeaders: Map<String, String> = emptyMap(),
)
