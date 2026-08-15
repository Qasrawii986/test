package com.smsexpense.tracker.domain.usecase

import com.smsexpense.tracker.data.remote.api.UpdateApi
import com.smsexpense.tracker.domain.model.UpdateCheck
import java.io.IOException

/**
 * Compares the published release's versionCode against the running build.
 * Pure logic + one network call, so it is fully unit-testable with a fake API.
 */
class CheckForUpdateUseCase(
    private val api: UpdateApi,
    private val currentVersionCode: Int,
    private val currentVersionName: String,
) {
    suspend operator fun invoke(): UpdateCheck = try {
        val latest = api.fetchLatest()
        if (latest.versionCode > currentVersionCode) {
            UpdateCheck.Available(latest)
        } else {
            UpdateCheck.UpToDate(currentVersionName)
        }
    } catch (e: IOException) {
        UpdateCheck.Failed(e.message ?: "Network error")
    } catch (e: Exception) {
        UpdateCheck.Failed(e.message ?: e.javaClass.simpleName)
    }
}
