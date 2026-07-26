package com.example.bioguard_wearos.presentation.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bioguard_wearos.data.local.BioGuardPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SplashUiState(
    val isFirstRun: Boolean = true,
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val preferences: BioGuardPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val firstRun = preferences.isFirstRun.first()
            val loggedIn = preferences.isLoggedIn.first()
            _uiState.update { it.copy(isFirstRun = firstRun, isLoggedIn = loggedIn, isLoading = false) }
        }
    }
}
