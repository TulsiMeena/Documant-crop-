package com.example.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PreferencesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserPreferencesRepository(application)

    val isOnboardingCompleted: StateFlow<Boolean?> = repository.isOnboardingCompleted.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val appTheme: StateFlow<String> = repository.appTheme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "SYSTEM"
    )

    val defaultScanMode: StateFlow<String> = repository.defaultScanMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "AUTO"
    )

    val autoCropEnabled: StateFlow<Boolean> = repository.autoCropEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch {
            repository.setOnboardingCompleted(completed)
        }
    }

    fun setAppTheme(theme: String) {
        viewModelScope.launch {
            repository.setAppTheme(theme)
        }
    }

    fun setDefaultScanMode(mode: String) {
        viewModelScope.launch {
            repository.setDefaultScanMode(mode)
        }
    }

    fun setAutoCropEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoCropEnabled(enabled)
        }
    }
}
