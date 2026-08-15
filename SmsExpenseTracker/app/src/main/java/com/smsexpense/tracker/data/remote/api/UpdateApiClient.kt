package com.smsexpense.tracker.data.remote.api

import com.smsexpense.tracker.domain.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Reads release metadata from GitHub's "latest release" redirect endpoints:
 *
 *   https://github.com/<owner>/<repo>/releases/latest/download/update.json
 *
 * That URL always resolves to the newest published release's asset, so no API
 * token and no rate-limited API call are involved (the repo is public).
 */
interface UpdateApi {
    suspend fun fetchLatest(): UpdateInfo
    /** Downloads the APK to [target], reporting 0..1 progress. */
    suspend fun downloadApk(url: String, target: File, onProgress: (Float) -> Unit)
}

class GithubUpdateApi(
    private val owner: String,
    private val repo: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://github.com",
) : UpdateApi {

    private fun latestAssetUrl(name: String) =
        "$baseUrl/$owner/$repo/releases/latest/download/$name"

    override suspend fun fetchLatest(): UpdateInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(latestAssetUrl(METADATA_ASSET)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Update check failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            parse(body, fallbackApkUrl = latestAssetUrl(APK_ASSET))
        }
    }

    override suspend fun downloadApk(
        url: String,
        target: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty download response")
            val total = body.contentLength()
            target.parentFile?.mkdirs()
            if (target.exists()) target.delete()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                    }
                    output.flush()
                }
            }
        }
    }

    companion object {
        const val METADATA_ASSET = "update.json"
        const val APK_ASSET = "app-release.apk"

        /** Parsed separately so it can be unit-tested without any network. */
        fun parse(json: String, fallbackApkUrl: String): UpdateInfo {
            val obj = JSONObject(json)
            val versionCode = obj.optInt("versionCode", -1)
            if (versionCode <= 0) throw IOException("Malformed update.json: missing versionCode")
            return UpdateInfo(
                versionCode = versionCode,
                versionName = obj.optString("versionName", "?"),
                notes = obj.optString("notes", ""),
                apkUrl = obj.optString("apkUrl").ifBlank { fallbackApkUrl },
            )
        }
    }
}
