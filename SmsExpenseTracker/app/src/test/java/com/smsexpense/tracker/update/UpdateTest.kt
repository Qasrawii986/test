package com.smsexpense.tracker.update

import com.smsexpense.tracker.data.remote.api.GithubUpdateApi
import com.smsexpense.tracker.data.remote.api.UpdateApi
import com.smsexpense.tracker.domain.model.UpdateCheck
import com.smsexpense.tracker.domain.model.UpdateInfo
import com.smsexpense.tracker.domain.usecase.CheckForUpdateUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

/** Robolectric supplies the real org.json; the plain JVM stub throws "not mocked". */
@RunWith(RobolectricTestRunner::class)
class UpdateMetadataParsingTest {

    private val fallback = "https://github.com/o/r/releases/latest/download/app-release.apk"

    @Test
    fun `parses a full update json`() {
        val info = GithubUpdateApi.parse(
            """{"versionCode":7,"versionName":"1.4.0","notes":"Fixes","apkUrl":"https://x/y.apk"}""",
            fallback,
        )
        assertEquals(7, info.versionCode)
        assertEquals("1.4.0", info.versionName)
        assertEquals("Fixes", info.notes)
        assertEquals("https://x/y.apk", info.apkUrl)
    }

    @Test
    fun `falls back to the latest-download url when apkUrl is missing`() {
        val info = GithubUpdateApi.parse("""{"versionCode":7,"versionName":"1.4.0"}""", fallback)
        assertEquals(fallback, info.apkUrl)
        assertEquals("", info.notes)
    }

    @Test(expected = IOException::class)
    fun `rejects metadata without a version code`() {
        GithubUpdateApi.parse("""{"versionName":"1.4.0"}""", fallback)
    }

    @Test(expected = Exception::class)
    fun `rejects malformed json`() {
        GithubUpdateApi.parse("not json at all", fallback)
    }
}

class CheckForUpdateUseCaseTest {

    private class FakeApi(
        private val info: UpdateInfo? = null,
        private val error: Exception? = null,
    ) : UpdateApi {
        override suspend fun fetchLatest(): UpdateInfo = error?.let { throw it } ?: info!!
        override suspend fun downloadApk(url: String, target: File, onProgress: (Float) -> Unit) = Unit
    }

    private fun info(code: Int) = UpdateInfo(code, "9.9.9", "notes", "https://x/y.apk")

    @Test
    fun `newer version code is reported as available`() = runTest {
        val result = CheckForUpdateUseCase(FakeApi(info(6)), currentVersionCode = 5, currentVersionName = "1.3.0")()
        assertTrue(result is UpdateCheck.Available)
        assertEquals(6, (result as UpdateCheck.Available).info.versionCode)
    }

    @Test
    fun `same version code is up to date`() = runTest {
        val result = CheckForUpdateUseCase(FakeApi(info(5)), 5, "1.3.0")()
        assertTrue(result is UpdateCheck.UpToDate)
    }

    @Test
    fun `older published version never triggers a downgrade`() = runTest {
        val result = CheckForUpdateUseCase(FakeApi(info(4)), 5, "1.3.0")()
        assertTrue(result is UpdateCheck.UpToDate)
    }

    @Test
    fun `network failure is reported, never crashes`() = runTest {
        val result = CheckForUpdateUseCase(FakeApi(error = IOException("offline")), 5, "1.3.0")()
        assertTrue(result is UpdateCheck.Failed)
        assertEquals("offline", (result as UpdateCheck.Failed).message)
    }
}
