package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.SettingsRepository
import com.example.data.service.Pbkdf2PinStorageService
import com.example.data.service.PinStorageService
import com.example.util.PinStrength
import com.example.util.PinStrengthAnalyzer
import com.example.util.SecurityUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PinChangeViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application.applicationContext)
    private val pinStorageService: PinStorageService = Pbkdf2PinStorageService()

    // Input States
    val currentPin = MutableStateFlow("")
    val newPin = MutableStateFlow("")
    val confirmPin = MutableStateFlow("")

    // Status states
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage = _successMessage.asStateFlow()

    private val _recoveryKey = MutableStateFlow<String?>(null)
    val recoveryKey = _recoveryKey.asStateFlow()

    // Stored states
    val developerPinHash: StateFlow<String?> = settingsRepository.developerPinHash
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    val developerPinSalt: StateFlow<String?> = settingsRepository.developerPinSalt
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val pinSnapshot: StateFlow<com.example.data.service.PinSnapshot?> = kotlinx.coroutines.flow.combine(
        settingsRepository.developerPinHash,
        settingsRepository.developerPinSalt,
        settingsRepository.developerPinIterations
    ) { h, s, i -> com.example.data.service.PinSnapshot(h, s, i) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val pinChecker = com.example.data.service.PinChecker()

    // Form validation state combining input states
    val isFormValid: StateFlow<Boolean> = combine(
        currentPin, newPin, confirmPin, developerPinHash, developerPinSalt
    ) { current, new, confirm, hash, salt ->
        val currentValid = if (hash == null || salt == null) {
            current == "000000"
        } else {
            current.isNotEmpty()
        }
        val pinStrength = PinStrengthAnalyzer.analyze(new)
        val newValid = new.length in 4..6 && pinStrength != PinStrength.WEAK
        val confirmValid = new == confirm
        currentValid && newValid && confirmValid
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun onCurrentPinChanged(value: String) {
        if (value.all { it.isDigit() } && value.length <= 6) {
            currentPin.value = value
            _errorMessage.value = null
        }
    }

    fun onNewPinChanged(value: String) {
        if (value.all { it.isDigit() } && value.length <= 6) {
            newPin.value = value
            _errorMessage.value = null
        }
    }

    fun onConfirmPinChanged(value: String) {
        if (value.all { it.isDigit() } && value.length <= 6) {
            confirmPin.value = value
            _errorMessage.value = null
        }
    }

    fun executePinChange(onSuccess: (String) -> Unit) {
        val current = currentPin.value
        val new = newPin.value
        val confirm = confirmPin.value

        if (new != confirm) {
            _errorMessage.value = "PIN confirmation does not match."
            return
        }

        val strength = PinStrengthAnalyzer.analyze(new)
        if (strength == PinStrength.WEAK) {
            _errorMessage.value = "PIN does not meet strength requirements."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val outcome = pinChecker.check(current, pinSnapshot.value)
                if (outcome.result == com.example.data.service.PinGate.Result.NOT_READY) {
                    _errorMessage.value = "Still loading. Please try again in a moment."
                    _isLoading.value = false
                    return@launch
                }
                if (outcome.result != com.example.data.service.PinGate.Result.OK) {
                    _errorMessage.value = "Current PIN is incorrect."
                    _isLoading.value = false
                    return@launch
                }

                // Generate new salt and slow hash using standard PBKDF2WithHmacSHA256
                val newSalt = pinStorageService.generateSalt()
                val newHash = pinStorageService.hashPin(new, newSalt, com.example.data.service.PinGate.CURRENT_ITERATIONS)
                val strengthStr = strength.displayName
                val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                val newRecoveryKey = SecurityUtils.generateRecoveryKey()

                settingsRepository.saveDeveloperPin(newHash, newSalt, strengthStr, dateStr, newRecoveryKey)
                
                _recoveryKey.value = newRecoveryKey
                _successMessage.value = "PIN changed successfully!"
                onSuccess(newRecoveryKey)
            } catch (e: Exception) {
                _errorMessage.value = "An error occurred: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetForm() {
        currentPin.value = ""
        newPin.value = ""
        confirmPin.value = ""
        _errorMessage.value = null
        _successMessage.value = null
        _recoveryKey.value = null
    }
}
