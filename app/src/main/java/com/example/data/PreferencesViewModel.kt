package com.example.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PreferencesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserPreferencesRepository(application)

    private val _isSessionUnlocked = MutableStateFlow(false)
    val isSessionUnlocked: StateFlow<Boolean> = _isSessionUnlocked.asStateFlow()

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

    val gameDisguiseEnabled: StateFlow<Boolean> = repository.gameDisguiseEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val gameDisguisePin: StateFlow<String> = repository.gameDisguisePin.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
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

    fun setGameDisguiseEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setGameDisguiseEnabled(enabled)
        }
    }

    fun setGameDisguisePin(pin: String) {
        viewModelScope.launch {
            repository.setGameDisguisePin(pin)
        }
    }

    fun setSessionUnlocked(unlocked: Boolean) {
        _isSessionUnlocked.value = unlocked
    }

    fun lockSession() {
        _isSessionUnlocked.value = false
    }
}
