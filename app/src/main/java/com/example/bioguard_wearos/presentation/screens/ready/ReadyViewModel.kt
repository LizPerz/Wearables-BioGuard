package com.example.bioguard_wearos.presentation.screens.ready

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bioguard_wearos.data.local.BioGuardPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReadyViewModel @Inject constructor(
    private val preferences: BioGuardPreferences
) : ViewModel() {

    fun confirmAndStart() {
        viewModelScope.launch {
            preferences.setFirstRunComplete()
        }
    }
}
