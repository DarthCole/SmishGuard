package com.smishguard.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.smishguard.app.data.local.SmishGuardDatabase
import kotlinx.coroutines.launch

/*
 * SettingsViewModel.kt — ViewModel for SettingsFragment
 * =======================================================
 * Handles the settings screen's data and operations.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_FILE = "smishguard_secure_prefs"
        const val KEY_HISTORY_CLEARED_AT = "history_cleared_at"
    }

    private val database = SmishGuardDatabase.getInstance(application)
    private val prefs = EncryptedSharedPreferences.create(
        PREFS_FILE,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        application,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _historyCleared = MutableLiveData<Boolean>()
    val historyCleared: LiveData<Boolean> = _historyCleared

    /**
     * Delete all stored analysis results from the local database
     * and record the current time so that scanUnanalyzedMessages()
     * does not re-scan messages that existed before the clear.
     */
    fun clearAnalysisHistory() {
        viewModelScope.launch {
            database.analysisResultDao().deleteAllResults()
            prefs.edit().putLong(KEY_HISTORY_CLEARED_AT, System.currentTimeMillis()).apply()
            _historyCleared.value = true
        }
    }
}
