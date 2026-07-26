package com.example.bioguard_wearos.presentation.screens.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.bioguard_wearos.data.local.BioGuardPreferences
import com.example.bioguard_wearos.domain.model.SensorData
import com.example.bioguard_wearos.domain.repository.SensorDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

data class DashboardUiState(
    val sensorData: SensorData = SensorData(),
    val isPhoneConnected: Boolean = false,
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
    private val preferences: BioGuardPreferences
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
            val firstRun = preferences.isFirstRun.first()
            _uiState.update { it.copy(isFirstRun = firstRun) }
        }
    }

    fun setPhoneConnected(connected: Boolean) {
        _uiState.update { it.copy(isPhoneConnected = connected) }
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