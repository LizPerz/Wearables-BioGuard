package com.example.bioguard_wearos.presentation.screens.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bioguard_wearos.data.local.BioGuardPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val hasToken: Boolean = false,
    val isChecking: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val preferences: BioGuardPreferences
) : ViewModel() {

    companion object {
        private const val TAG = "BIOGUARD_LOGIN"
    }

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        checkToken()
    }

    fun checkToken() {
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true) }
            delay(1000L)
            val loggedIn = preferences.isLoggedIn.first()
            Log.d(TAG, "Verificando token: isLoggedIn=$loggedIn")
            _uiState.update { it.copy(hasToken = loggedIn, isChecking = false) }
        }
    }
}
