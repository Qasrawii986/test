package com.smsexpense.tracker.ui.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpense.tracker.data.remote.api.UpdateApi
import com.smsexpense.tracker.domain.model.UpdateCheck
import com.smsexpense.tracker.domain.model.UpdateInfo
import com.smsexpense.tracker.domain.usecase.CheckForUpdateUseCase
import com.smsexpense.tracker.service.update.ApkInstaller
import com.smsexpense.tracker.service.update.InstallResultReceiver
import com.smsexpense.tracker.util.AppLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class UpdateStage {
    data object Idle : UpdateStage()
    data object Checking : UpdateStage()
    data class UpToDate(val versionName: String) : UpdateStage()
    data class Available(val info: UpdateInfo) : UpdateStage()
    data class Downloading(val progress: Float) : UpdateStage()
    data class ReadyToInstall(val info: UpdateInfo) : UpdateStage()
    /** Handed to Android: its confirm/progress screen is on top of us now. */
    data class Installing(val info: UpdateInfo) : UpdateStage()
    /** Shown after the process comes back up on the new version. */
    data class Installed(val versionName: String) : UpdateStage()
    data class Error(val message: String) : UpdateStage()
}

data class UpdateUiState(
    val stage: UpdateStage = UpdateStage.Idle,
    val currentVersionName: String = "",
    val currentVersionCode: Int = 0,
    val canInstall: Boolean = false,
)

class UpdateViewModel(
    application: Application,
    private val api: UpdateApi,
    private val checkForUpdate: CheckForUpdateUseCase,
    private val settings: com.smsexpense.tracker.domain.repository.SettingsRepository,
    currentVersionName: String,
    private val currentVersionCode: Int,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        UpdateUiState(
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
            canInstall = ApkInstaller.canInstall(application),
        )
    )

    val uiState: StateFlow<UpdateUiState> = _uiState

    private var downloadedApk: File? = null
    private var fallbackAttempted = false

    init {
        reconcilePendingInstall()
    }

    fun refreshInstallPermission() {
        _uiState.value = _uiState.value.copy(
            canInstall = ApkInstaller.canInstall(getApplication())
        )
        reconcilePendingInstall()
    }

    fun check() {
        _uiState.value = _uiState.value.copy(stage = UpdateStage.Checking)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                stage = when (val result = checkForUpdate()) {
                    is UpdateCheck.Available -> UpdateStage.Available(result.info)
                    is UpdateCheck.UpToDate -> UpdateStage.UpToDate(result.currentVersionName)
                    is UpdateCheck.Failed -> UpdateStage.Error(result.message)
                }
            )
        }
    }

    fun download() {
        val info = (_uiState.value.stage as? UpdateStage.Available)?.info ?: return
        _uiState.value = _uiState.value.copy(stage = UpdateStage.Downloading(0f))
        viewModelScope.launch {
            val target = File(getApplication<Application>().cacheDir, "updates/app-update.apk")
            try {
                api.downloadApk(info.apkUrl, target) { progress ->
                    _uiState.value = _uiState.value.copy(stage = UpdateStage.Downloading(progress))
                }
                downloadedApk = target
                fallbackAttempted = false
                _uiState.value = _uiState.value.copy(stage = UpdateStage.ReadyToInstall(info))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    stage = UpdateStage.Error("Download failed: ${e.message ?: e.javaClass.simpleName}")
                )
            }
        }
    }

    fun install() {
        val apk = downloadedApk ?: return
        val info = (_uiState.value.stage as? UpdateStage.ReadyToInstall)?.info
            ?: (_uiState.value.stage as? UpdateStage.Installing)?.info
            ?: return
        viewModelScope.launch {
            InstallResultReceiver.lastError = null
            // Remember what we are installing: this process is about to be killed
            // and replaced, so success can only be confirmed after it restarts.
            settings.setPendingUpdateVersion(info.versionCode, info.versionName)
            val error = ApkInstaller.install(getApplication(), apk)
            _uiState.value = _uiState.value.copy(
                stage = if (error != null) UpdateStage.Error(error) else UpdateStage.Installing(info)
            )
        }
    }

    /**
     * Called when the screen resumes. If the running build is at least the version
     * we handed to Android, the update went through (the process was replaced).
     */
    private fun reconcilePendingInstall() {
        viewModelScope.launch {
            val pending = settings.pendingUpdateVersion.first()
            if (pending.first <= 0) return@launch
            if (currentVersionCode >= pending.first) {
                settings.clearPendingUpdateVersion()
                _uiState.value = _uiState.value.copy(stage = UpdateStage.Installed(pending.second))
            }
        }
    }

    /**
     * A session can also fail asynchronously (the commit succeeds, then the
     * platform reports an error) — that is how OEM self-update restrictions
     * surface. Retry once through the system installer activity before giving up.
     */
    fun consumeSessionError() {
        val message = InstallResultReceiver.lastError ?: return
        InstallResultReceiver.lastError = null
        val apk = downloadedApk
        if (apk != null && !fallbackAttempted) {
            fallbackAttempted = true
            if (ApkInstaller.openWithSystemInstaller(getApplication(), apk)) {
                AppLog.d("Session install rejected; handed APK to the system installer")
                return
            }
        }
        _uiState.value = _uiState.value.copy(stage = UpdateStage.Error(message))
    }

    /** Explicit escape hatch shown on the error state. */
    fun installViaSystemInstaller() {
        val apk = downloadedApk ?: return
        if (!ApkInstaller.openWithSystemInstaller(getApplication(), apk)) {
            _uiState.value = _uiState.value.copy(
                stage = UpdateStage.Error("Could not open the system installer")
            )
        }
    }

    /** True once the downloaded APK exists, enabling the manual install button. */
    fun hasDownloadedApk(): Boolean = downloadedApk?.exists() == true
}
