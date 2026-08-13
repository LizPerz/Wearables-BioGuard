package com.example.bioguard_wearos.presentation.screens.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bioguard_wearos.data.local.BioGuardPreferences
import com.example.bioguard_wearos.domain.model.SensorData
import com.example.bioguard_wearos.domain.repository.SensorDataRepository
import com.example.bioguard_wearos.domain.repository.SyncRepository
import com.example.bioguard_wearos.domain.risk.AlertManager
import com.example.bioguard_wearos.domain.risk.BiometricInput
import com.example.bioguard_wearos.domain.risk.RiskAssessment
import com.example.bioguard_wearos.domain.risk.RiskLevel
import com.example.bioguard_wearos.domain.risk.TriggerSource
import com.example.bioguard_wearos.domain.usecase.StartSensorsUseCase
import com.example.bioguard_wearos.util.WearablePairingQr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val sensorData: SensorData = SensorData(),
    val isPhonePaired: Boolean = false,
    val isPhoneConnected: Boolean = false,
    val sensorStatusMessage: String? = null,
    val panicStatusMessage: String? = null,
    val isSendingPanic: Boolean = false,
    val isFirstRun: Boolean = true,
    val showDetailDialog: Boolean = false,
    val dialogTitle: String = "",
    val dialogValue: String = "--",
    val dialogUnit: String = "",
    val dialogIcon: DetailIcon = DetailIcon.HEART,
    val dialogHrvRmssd: Float = 0f,
    val dialogHrvSdnn: Float = 0f,
    val dialogStressLabel: String = ""
)

enum class DetailIcon { HEART, THERMOMETER, DROPS }

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val sensorDataRepository: SensorDataRepository,
    private val preferences: BioGuardPreferences,
    private val syncRepository: SyncRepository,
    private val alertManager: AlertManager,
    private val startSensorsUseCase: StartSensorsUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "BIOGUARD_DASH_VM"
    }

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sensorDataRepository.sensorData.collect { data ->
                Log.d(TAG, "SensorData recibido: BPM=${data.bpm}, Temp=${data.temperature}, GSR=${data.gsr}")
                _uiState.update { it.copy(sensorData = data) }
            }
        }
        viewModelScope.launch {
            sensorDataRepository.sensorAvailability.collect { availability ->
                _uiState.update { it.copy(sensorStatusMessage = availability.statusMessage) }
            }
        }
        viewModelScope.launch {
            val firstRun = preferences.isFirstRun.first()
            _uiState.update { it.copy(isFirstRun = firstRun) }
        }
        // Garantiza que la captura de sensores esté activa mientras el dashboard
        // está en pantalla (idempotente: startSensors ignora arranques duplicados).
        viewModelScope.launch {
            try {
                startSensorsUseCase()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudieron iniciar sensores desde el dashboard: ${e.message}")
            }
        }
    }

    fun setPhoneConnected(connected: Boolean) {
        _uiState.update { it.copy(isPhoneConnected = connected) }
    }

    fun setPhoneConnectionStatus(paired: Boolean, connected: Boolean) {
        _uiState.update { it.copy(isPhonePaired = paired, isPhoneConnected = connected) }
    }

    suspend fun getInstallId(): String = preferences.getOrCreateInstallId()

    suspend fun getTrustedMobileNodeId(): String? = preferences.getTrustedMobileNodeId()

    fun unlinkPhone() {
        viewModelScope.launch {
            runCatching {
                preferences.clearTrustedMobileNodeId()
            }.onSuccess {
                _uiState.update { it.copy(isPhonePaired = false, isPhoneConnected = false) }
            }.onFailure { e ->
                Log.w(TAG, "No se pudo desvincular el movil: ${e.message}")
            }
        }
    }

    suspend fun createPairingPayload(deviceName: String, deviceId: String, nodeId: String): String {
        val issuedAt = System.currentTimeMillis() / 1000L
        val nonce = WearablePairingQr.newNonce()
        preferences.savePairingChallenge(nonce, issuedAt)
        return WearablePairingQr.buildPayload(deviceName, deviceId, nodeId, issuedAt, nonce)
    }

    fun triggerPanicButton() {
        if (_uiState.value.isSendingPanic) return
        val current = uiState.value.sensorData
        Log.w(TAG, "BOTON DE PANICO ACTIVADO MANUALMENTE POR EL USUARIO")
        _uiState.update {
            it.copy(
                isSendingPanic = true,
                panicStatusMessage = "Alerta de panico activada"
            )
        }
        viewModelScope.launch {
            try {
                val assessment = RiskAssessment(
                    level = RiskLevel.CRITICAL_HIGH,
                    probability = 1.0f,
                    triggerSource = TriggerSource.SIGMOID,
                    input = BiometricInput(bpm = current.bpm, temperature = current.temperature, gsr = current.gsr, bmi = 25f)
                )
                alertManager.triggerAlert(assessment)
                val result = syncRepository.enviarAlerta(
                    tipoAlerta = "PANIC_BUTTON",
                    mensaje = "Alerta manual activada desde el reloj",
                    nivelRiesgo = "CRITICAL_HIGH",
                    bpm = current.bpm,
                    temperatura = current.temperature,
                    gsr = current.gsr
                )
                _uiState.update {
                    it.copy(
                        isSendingPanic = false,
                        panicStatusMessage = if (result.isSuccess) {
                            "SOS guardado y en envio prioritario"
                        } else {
                            "SOS local activo; se enviara al reconectar"
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error disparando botón de pánico: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isSendingPanic = false,
                        panicStatusMessage = "SOS local activo; revisa conexion con el movil"
                    )
                }
            }
        }
    }

    fun showDetailDialog(
        title: String,
        value: String,
        unit: String,
        icon: DetailIcon,
        hrvRmssd: Float = 0f,
        hrvSdnn: Float = 0f,
        stressLabel: String = ""
    ) {
        _uiState.update {
            it.copy(
                showDetailDialog = true,
                dialogTitle = title,
                dialogValue = value,
                dialogUnit = unit,
                dialogIcon = icon,
                dialogHrvRmssd = hrvRmssd,
                dialogHrvSdnn = hrvSdnn,
                dialogStressLabel = stressLabel
            )
        }
    }

    fun dismissDetailDialog() {
        _uiState.update { it.copy(showDetailDialog = false) }
    }
}
