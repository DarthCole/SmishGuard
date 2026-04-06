package com.smishguard.app.ui.conversations

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.smishguard.app.data.repository.SmsRepository
import com.smishguard.app.domain.model.SmsConversation
import com.smishguard.app.ml.SmishDetector
import kotlinx.coroutines.launch

/*
 * ConversationsViewModel.kt — ViewModel for ConversationsFragment
 * ==================================================================
 * Manages the list of SMS conversations displayed in the UI.
 *
 * "viewModelScope" is a pre-built CoroutineScope tied to this ViewModel's
 * lifecycle. When the ViewModel is cleared (e.g., the Fragment is destroyed),
 * all coroutines launched in this scope are automatically cancelled.
 *
 * "viewModelScope.launch { }" starts a new coroutine that runs concurrently.
 * Think of it as "do this work in the background."
 */
class ConversationsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ConversationsViewModel"
    }

    private val repository = SmsRepository(application)
    private val smishDetector = SmishDetector.getInstance(application)

    // ── LiveData for the conversation list ──
    private val _conversations = MutableLiveData<List<SmsConversation>>()
    val conversations: LiveData<List<SmsConversation>> = _conversations

    // ── LiveData for loading state (to show/hide a progress spinner) ──
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // ── LiveData for error messages ──
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * Load all SMS conversations from the device.
     * First scans any unanalyzed messages, then loads the conversation list.
     * Called when the Fragment becomes visible.
     */
    fun loadConversations() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Scan any messages that haven't been analyzed yet
                Log.d(TAG, "Scanning unanalyzed messages...")
                repository.scanUnanalyzedMessages(smishDetector)
                Log.d(TAG, "Scan complete, loading conversations...")

                val result = repository.getConversations()
                _conversations.value = result
            } catch (e: Exception) {
                _error.value = "Failed to load conversations: ${e.message}"
                // "${e.message}" is string interpolation — embeds the exception message
            } finally {
                // "finally" ALWAYS runs, whether try succeeded or caught an exception
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Don't close the singleton — it's shared across components
    }
}
