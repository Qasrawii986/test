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
    currentVersionName: String,
    currentVersionCode: Int,
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

    fun refreshInstallPermission() {
        _uiState.value = _uiState.value.copy(
            canInstall = ApkInstaller.canInstall(getApplication())
        )
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
        viewModelScope.launch {
            InstallResultReceiver.lastError = null
            val error = ApkInstaller.install(getApplication(), apk)
            if (error != null) {
                _uiState.value = _uiState.value.copy(stage = UpdateStage.Error(error))
            } else if (info != null) {
                // System install UI is now in charge; keep the ready state so the
                // user can retry if they dismiss it.
                _uiState.value = _uiState.value.copy(stage = UpdateStage.ReadyToInstall(info))
            }
        }
    }

    /** Called when returning to the screen: surfaces a failure reported by the session. */
    fun consumeSessionError() {
        InstallResultReceiver.lastError?.let { message ->
            InstallResultReceiver.lastError = null
            _uiState.value = _uiState.value.copy(stage = UpdateStage.Error(message))
        }
    }
}
