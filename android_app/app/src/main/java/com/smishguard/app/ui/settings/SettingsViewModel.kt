package com.smishguard.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.smishguard.app.data.local.SmishGuardDatabase
import kotlinx.coroutines.launch

/*
 * SettingsViewModel.kt — ViewModel for SettingsFragment
 * =======================================================
 * Handles the settings screen's data and operations.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SmishGuardDatabase.getInstance(application)

    private val _historyCleared = MutableLiveData<Boolean>()
    val historyCleared: LiveData<Boolean> = _historyCleared

    /**
     * Delete all stored analysis results from the local database.
     * This is a user-facing "clear data" action for privacy.
     */
    fun clearAnalysisHistory() {
        viewModelScope.launch {
            database.analysisResultDao().deleteAllResults()
            _historyCleared.value = true
        }
    }
}
