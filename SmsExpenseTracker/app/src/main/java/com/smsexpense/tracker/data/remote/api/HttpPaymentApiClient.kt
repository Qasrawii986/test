package com.smsexpense.tracker.data.remote.api

import com.smsexpense.tracker.data.remote.dto.PaymentDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * POST /payments over OkHttp. When the API is disabled in settings this is a no-op
 * returning [ApiResult.Disabled], so the app is fully offline-first by default.
 */
class HttpPaymentApiClient(
    private val configProvider: ApiConfigProvider,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) : PaymentApiClient {

    override suspend fun postPayment(payment: PaymentDto): ApiResult = withContext(Dispatchers.IO) {
        val config = configProvider.current()
        if (!config.enabled || config.baseUrl.isBlank()) return@withContext ApiResult.Disabled

        val url = config.baseUrl.trimEnd('/') + "/payments"
        val requestBuilder = Request.Builder()
            .url(url)
            .post(payment.toJson().toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (config.authToken.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.authToken}")
        }
        config.extraHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                when {
                    response.isSuccessful -> ApiResult.Success
                    response.code in 500..599 ->
                        ApiResult.Failure("Server error ${response.code}", retryable = true)
                    else ->
                        ApiResult.Failure("Rejected: HTTP ${response.code}", retryable = false)
                }
            }
        } catch (e: IOException) {
            ApiResult.Failure("Network error: ${e.message ?: e.javaClass.simpleName}", retryable = true)
        } catch (e: IllegalArgumentException) {
            ApiResult.Failure("Bad base URL: ${e.message}", retryable = false)
        }
    }
}
