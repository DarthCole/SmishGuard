package com.smishguard.app.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/*
 * MainViewModel.kt — ViewModel for MainActivity
 * =================================================
 * A "ViewModel" holds UI state and survives configuration changes
 * (like screen rotation). Without a ViewModel, rotating the phone
 * would destroy and recreate the Activity, losing all state.
 *
 * "AndroidViewModel" is a ViewModel subclass that receives the
 * Application context — needed to access SharedPreferences.
 *
 * LIVEDATA explained:
 *   - MutableLiveData: can be read AND written (used inside the ViewModel)
 *   - LiveData: can only be READ (exposed to the UI via public property)
 *   - When the value changes, all observers (the Activity/Fragment) are
 *     automatically notified and update the UI.
 *
 * "private val _isProtectionEnabled" with underscore prefix is a convention:
 *   - _privateVersion: MutableLiveData (writable, private)
 *   - publicVersion: LiveData (read-only, exposed)
 *   This prevents the UI from accidentally modifying the data directly.
 *
 * ENCRYPTED SHARED PREFERENCES:
 *   Normal SharedPreferences store data as plain text on disk.
 *   EncryptedSharedPreferences uses AES-256 encryption, so even if
 *   someone gains access to the device's file system, they can't read
 *   the stored settings. This is an OWASP MASVS requirement.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ── LiveData for protection toggle state ──
    private val _isProtectionEnabled = MutableLiveData<Boolean>()
    val isProtectionEnabled: LiveData<Boolean> = _isProtectionEnabled
    // "LiveData<Boolean>" — the UI can observe but not modify

    // Encrypted preferences for storing settings securely
    private val encryptedPrefs = createEncryptedPreferences(application)

    companion object {
        private const val PREFS_FILE = "smishguard_secure_prefs"
        private const val KEY_PROTECTION_ENABLED = "protection_enabled"
    }

    /*
     * "init { }" block runs when the class is INSTANTIATED (created).
     * It's like a constructor body. Useful for setting up initial state.
     */
    init {
        // Load the saved protection state (defaults to false)
        _isProtectionEnabled.value = encryptedPrefs.getBoolean(
            KEY_PROTECTION_ENABLED, false
        )
    }

    /**
     * Update the protection enabled state and persist it.
     */
    fun setProtectionEnabled(enabled: Boolean) {
        _isProtectionEnabled.value = enabled
        encryptedPrefs.edit()
            .putBoolean(KEY_PROTECTION_ENABLED, enabled)
            .apply()
        // ".apply()" saves asynchronously (non-blocking)
        // ".commit()" would save synchronously (blocks until written)
    }

    /**
     * Create EncryptedSharedPreferences using the Android Keystore.
     *
     * The Android Keystore is secure hardware (or secure software enclave)
     * that stores encryption keys. Even if the device is rooted, the keys
     * are protected by the hardware.
     */
    private fun createEncryptedPreferences(context: Context) =
        EncryptedSharedPreferences.create(
            PREFS_FILE,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            // ^ Creates (or retrieves) a master key stored in the Android Keystore
            // AES256_GCM_SPEC = 256-bit AES key with GCM authentication
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            // ^ Encrypts the preference KEYS with AES-256 SIV
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            // ^ Encrypts the preference VALUES with AES-256 GCM
        )
}
