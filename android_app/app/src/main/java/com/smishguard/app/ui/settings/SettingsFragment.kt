package com.smishguard.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.smishguard.app.databinding.FragmentSettingsBinding

/*
 * SettingsFragment.kt — Settings Screen
 * ========================================
 * Allows users to configure SmishGuard's behavior:
 *   - Toggle protection on/off
 *   - View privacy information
 *   - Clear analysis history
 *
 * This fragment uses the same ViewBinding pattern as ConversationsFragment.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SettingsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        setupPrivacyInfo()
        setupClearHistory()
        observeViewModel()
    }

    private fun setupPrivacyInfo() {
        binding.textPrivacyInfo.text = buildString {
            // "buildString { }" is a Kotlin utility that efficiently builds
            // a String by appending pieces. Like StringBuilder but cleaner.
            appendLine("Privacy & Security")
            appendLine("━━━━━━━━━━━━━━━━━━")
            appendLine("• All SMS analysis happens on your device")
            appendLine("• No message data is sent to any server")
            appendLine("• Analysis results are stored with AES-256 encryption")
            appendLine("• You can clear all analysis data at any time")
            appendLine("• SmishGuard never modifies or deletes your messages")
        }
    }

    private fun setupClearHistory() {
        binding.buttonClearHistory.setOnClickListener {
            viewModel.clearAnalysisHistory()
        }
    }

    private fun observeViewModel() {
        viewModel.historyCleared.observe(viewLifecycleOwner) { cleared ->
            if (cleared) {
                binding.textClearStatus.text = "Analysis history cleared"
                binding.textClearStatus.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
