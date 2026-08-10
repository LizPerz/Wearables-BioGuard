package com.example.bioguard_wearos.presentation.screens.alert

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bioguard_wearos.domain.repository.SyncRepository
import com.example.bioguard_wearos.domain.risk.AlertManager
import com.example.bioguard_wearos.domain.risk.AlertState
import com.example.bioguard_wearos.domain.risk.RiskLevel
import com.example.bioguard_wearos.presentation.WearableAlertNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CriticalAlertUiState(
    val alertState: AlertState = AlertState(),
    val countdownSeconds: Int = 60,
    val isCountdownActive: Boolean = false,
    val bpm: Float = 0f,
    val temperature: Float = 0f,
    val gsr: Float = 0f,
    val probability: Float = 0f,
    val eventSent: Boolean = false,
    val alertSent: Boolean = false,
    val helpRequested: Boolean = false,
    val helpSent: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CriticalAlertViewModel @Inject constructor(
    private val alertManager: AlertManager,
    private val syncRepository: SyncRepository
) : ViewModel() {

    companion object {
        private const val TAG = "BIOGUARD_ALERT_VM"
        private const val COUNTDOWN_TOTAL = 60
    }

    private val _uiState = MutableStateFlow(CriticalAlertUiState())
    val uiState: StateFlow<CriticalAlertUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            alertManager.alertState.collect { state ->
                if (state.isActive && !_uiState.value.eventSent) {
                    onCriticalAlertTriggered(state)
                }
            }
        }
    }

    private fun onCriticalAlertTriggered(state: AlertState) {
        Log.w(TAG, "ALERTA CRÍTICA: BPM=${state.assessment.input.bpm}, " +
                "Temp=${state.assessment.input.temperature}, GSR=${state.assessment.input.gsr}, " +
                "P(Pico)=${String.format("%.3f", state.assessment.probability)}")

        _uiState.update {
            it.copy(
                alertState = state,
                bpm = state.assessment.input.bpm,
                temperature = state.assessment.input.temperature,
                gsr = state.assessment.input.gsr,
                probability = state.assessment.probability,
                countdownSeconds = COUNTDOWN_TOTAL,
                isCountdownActive = true
            )
        }

        sendEvento()
        sendAlerta()
        startCountdown()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (i in COUNTDOWN_TOTAL downTo 0) {
                _uiState.update { it.copy(countdownSeconds = i) }
                if (i == 0) {
                    Log.w(TAG, "Temporizador expirado, protocolo secundario activado")
                    sendEventoUrgente()
                }
                delay(1000L)
            }
        }
    }

    fun dismissAlert() {
        Log.d(TAG, "Alerta descartada por el usuario")
        countdownJob?.cancel()
        alertManager.dismissAlert()
        _uiState.update { it.copy(isCountdownActive = false) }
        viewModelScope.launch {
            try {
                syncRepository.sendDismissCommandToPhone()
            } catch (e: Exception) {
                Log.w(TAG, "Error notificando descarte al movil: ${e.message}")
            }
        }
    }

    fun acknowledgeAlert(context: Context) {
        dismissAlert()
        val notifManager = NotificationManagerCompat.from(context)
        notifManager.cancel(WearableAlertNotifier.NOTIFICATION_CRITICAL)
    }

    fun requestHelp(context: Context) {
        Log.w(TAG, "USUARIO SOLICITÓ AYUDA DE EMERGENCIA")
        countdownJob?.cancel()
        alertManager.requestHelp()
        _uiState.update { it.copy(isCountdownActive = false, helpRequested = true) }

        val notifier = WearableAlertNotifier(context)
        notifier.showHelpRequestedNotification()

        sendEventoUrgenteWithFeedback(context)
    }

    private fun sendEvento() {
        val state = _uiState.value
        viewModelScope.launch {
            val result = syncRepository.enviarEvento(
                bpm = state.bpm,
                temperatura = state.temperature,
                gsr = state.gsr,
                nivelRiesgo = RiskLevel.CRITICAL_HIGH.name,
                tipoEvento = "CRITICAL_ALERT",
                descripcion = "Pico crítico detectado: P(Pico)=${state.probability}"
            )
            result.onSuccess {
                Log.d(TAG, "Evento enviado al servidor OK")
                _uiState.update { it.copy(eventSent = true) }
            }.onFailure { ex ->
                Log.w(TAG, "Error enviando evento: ${ex.message}")
                _uiState.update { it.copy(error = "Error enviando evento") }
            }
        }
    }

    private fun sendAlerta() {
        val state = _uiState.value
        viewModelScope.launch {
            val result = syncRepository.enviarAlerta(
                tipoAlerta = "CRITICAL_HYPOGLYCEMIA",
                mensaje = "Alerta crítica: Pico crítico detectado. BPM=${state.bpm}, Temp=${state.temperature}°C, GSR=${state.gsr}µS",
                nivelRiesgo = RiskLevel.CRITICAL_HIGH.name,
                bpm = state.bpm,
                temperatura = state.temperature,
                gsr = state.gsr
            )
            result.onSuccess {
                Log.d(TAG, "Alerta enviada al servidor OK")
                _uiState.update { it.copy(alertSent = true) }
            }.onFailure { ex ->
                Log.w(TAG, "Error enviando alerta: ${ex.message}")
            }
        }
    }

    private fun sendEventoUrgenteWithFeedback(context: Context) {
        val state = _uiState.value
        viewModelScope.launch {
            val eventResult = syncRepository.enviarEvento(
                bpm = state.bpm,
                temperatura = state.temperature,
                gsr = state.gsr,
                nivelRiesgo = RiskLevel.CRITICAL_HIGH.name,
                tipoEvento = "EMERGENCY_PROTOCOL",
                descripcion = "Protocolo de emergencia activado. Usuario solicitó ayuda manualmente."
            )
            val alertResult = syncRepository.enviarAlerta(
                tipoAlerta = "EMERGENCY assistance",
                mensaje = "Protocolo de auxilio a red familiar activado manualmente por el usuario",
                nivelRiesgo = RiskLevel.CRITICAL_HIGH.name,
                bpm = state.bpm,
                temperatura = state.temperature,
                gsr = state.gsr
            )

            val helpSent = eventResult.isSuccess || alertResult.isSuccess
            _uiState.update {
                it.copy(
                    helpSent = helpSent,
                    error = if (!helpSent) "Ayuda encolada. Se enviará al reconectar con el móvil." else null
                )
            }
            if (helpSent) {
                Log.d(TAG, "Solicitud de ayuda enviada correctamente")
            } else {
                Log.w(TAG, "Móvil no conectado. Ayuda encolada para envío diferido.")
            }
        }
    }

    private fun sendEventoUrgente() {
        val state = _uiState.value
        viewModelScope.launch {
            syncRepository.enviarEvento(
                bpm = state.bpm,
                temperatura = state.temperature,
                gsr = state.gsr,
                nivelRiesgo = RiskLevel.CRITICAL_HIGH.name,
                tipoEvento = "EMERGENCY_PROTOCOL",
                descripcion = "Protocolo de emergencia activado. Timer expirado."
            )
            syncRepository.enviarAlerta(
                tipoAlerta = "EMERGENCY assistance",
                mensaje = "Protocolo de auxilio a red familiar activado por temporizador",
                nivelRiesgo = RiskLevel.CRITICAL_HIGH.name,
                bpm = state.bpm,
                temperatura = state.temperature,
                gsr = state.gsr
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}
