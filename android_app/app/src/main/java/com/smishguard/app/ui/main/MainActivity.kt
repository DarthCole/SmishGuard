package com.smishguard.app.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.smishguard.app.R
import com.smishguard.app.databinding.ActivityMainBinding
import com.smishguard.app.service.SmsMonitorService
import com.smishguard.app.ui.conversations.ConversationsFragment
import com.smishguard.app.ui.settings.SettingsFragment

/*
 * MainActivity.kt — The Main Screen
 * ====================================
 * An "Activity" is a single screen with a user interface.
 * AppCompatActivity is the modern base class that provides backward
 * compatibility for older Android versions.
 *
 * This is the ENTRY POINT of the app (declared in AndroidManifest with
 * the LAUNCHER intent filter). When the user taps the app icon, Android
 * creates this Activity.
 *
 * LIFECYCLE (important to understand):
 *   onCreate()  → Activity is being created (set up UI here)
 *   onStart()   → Activity becomes visible
 *   onResume()  → Activity is in the foreground and interactive
 *   onPause()   → Another activity is coming to the foreground
 *   onStop()    → Activity is no longer visible
 *   onDestroy() → Activity is being destroyed
 *
 * VIEW BINDING explained:
 *   Instead of writing: val button = findViewById<Button>(R.id.myButton)
 *   With ViewBinding:   binding.myButton
 *   The "binding" object is auto-generated from activity_main.xml.
 *   Each View with an "android:id" becomes a property on the binding.
 *
 * "lateinit var" — will be initialized in onCreate(), not in the constructor.
 * "private" — only this class can access these properties.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    // Track which screen is showing so the menu icon can toggle
    private var isShowingSettings = false

    companion object {
        // Request code for runtime permission dialog — can be any unique integer
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    // The list of permissions our app needs
    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate (parse) the XML layout and create the binding object
        binding = ActivityMainBinding.inflate(layoutInflater)
        // Set the inflated layout as this Activity's content view
        setContentView(binding.root)

        // Get or create the ViewModel (survives screen rotation)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        // "ViewModelProvider(this)" scopes the ViewModel to this Activity's lifecycle
        // "[MainViewModel::class.java]" is the get() operator — retrieves by class type

        setupToolbar()
        setupProtectionToggle()
        observeViewModel()

        // Check and request permissions on first launch
        if (!hasAllPermissions()) {
            requestPermissions()
        } else {
            // Permissions already granted — load the conversations
            showConversationsFragment()
        }
    }

    /**
     * Set up the top app bar (toolbar).
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "SmishGuard"
    }

    /**
     * Set up the main protection ON/OFF switch.
     *
     * "setOnCheckedChangeListener" attaches a callback that fires
     * every time the switch state changes.
     * "{ _, isChecked -> }" is a LAMBDA (anonymous function):
     *   "_" means "I don't need this parameter" (it's the CompoundButton)
     *   "isChecked" is the new state (true = ON, false = OFF)
     */
    private fun setupProtectionToggle() {
        binding.switchProtection.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setProtectionEnabled(isChecked)

            if (isChecked) {
                startMonitoringService()
                binding.textProtectionStatus.text = "Protection is ON"
            } else {
                stopMonitoringService()
                binding.textProtectionStatus.text = "Protection is OFF"
            }
        }
    }

    /**
     * Observe LiveData from the ViewModel.
     *
     * "observe(this) { value -> ... }" means:
     *   "Watch this LiveData. Whenever the value changes, run this lambda.
     *    Stop watching when THIS activity is destroyed (lifecycle-aware)."
     */
    private fun observeViewModel() {
        viewModel.isProtectionEnabled.observe(this) { isEnabled ->
            // Update the switch without triggering the listener
            binding.switchProtection.isChecked = isEnabled
            binding.textProtectionStatus.text = if (isEnabled) {
                "Protection is ON"
            } else {
                "Protection is OFF"
            }
            // "if" in Kotlin is an EXPRESSION — it returns a value.
            // This is equivalent to Java's ternary: isEnabled ? "ON" : "OFF"
        }
    }

    /**
     * Start the foreground service that monitors incoming SMS.
     */
    private fun startMonitoringService() {
        val intent = Intent(this, SmsMonitorService::class.java)
        // "this" refers to the current Activity (which IS a Context)
        // "SmsMonitorService::class.java" is the target component's class reference
        ContextCompat.startForegroundService(this, intent)
    }

    /**
     * Stop the monitoring service.
     */
    private fun stopMonitoringService() {
        val intent = Intent(this, SmsMonitorService::class.java)
        stopService(intent)
    }

    /**
     * Load the ConversationsFragment into the fragment container.
     *
     * Fragments are "sub-screens" that live inside an Activity.
     * They have their own lifecycle and can be swapped in/out.
     */
    private fun showConversationsFragment() {
        supportFragmentManager.beginTransaction()
            .replace(
                com.smishguard.app.R.id.fragment_container,
                ConversationsFragment()
            )
            .commit()
        isShowingSettings = false
        invalidateOptionsMenu()
        // "replace" swaps whatever is in the container with the new Fragment
        // "commit" schedules the transaction (doesn't happen immediately)
    }

    /**
     * Load the SettingsFragment into the fragment container.
     */
    private fun showSettingsFragment() {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_container,
                SettingsFragment()
            )
            .commit()
        isShowingSettings = true
        invalidateOptionsMenu()
    }

    // ══════════════════════════════════════════════════════════
    // TOOLBAR MENU
    // ══════════════════════════════════════════════════════════

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val settingsItem = menu.findItem(R.id.action_settings)
        if (isShowingSettings) {
            settingsItem?.setIcon(R.drawable.ic_shield)
            settingsItem?.title = "Messages"
        } else {
            settingsItem?.setIcon(R.drawable.ic_settings)
            settingsItem?.title = "Settings"
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                if (isShowingSettings) {
                    showConversationsFragment()
                } else {
                    showSettingsFragment()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ══════════════════════════════════════════════════════════
    // RUNTIME PERMISSIONS
    // ══════════════════════════════════════════════════════════

    /**
     * Check if ALL required permissions are already granted.
     *
     * ".all { }" returns true only if EVERY element satisfies the condition.
     */
    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Show the system permission dialog.
     */
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            requiredPermissions,
            PERMISSION_REQUEST_CODE
        )
    }

    /**
     * Called by the system after the user responds to the permission dialog.
     *
     * "override" — this is a callback defined in Activity that we're implementing.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Check if ALL permissions were granted
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                showConversationsFragment()
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                // Show a message explaining why permissions are needed
                Toast.makeText(
                    this,
                    "SMS permissions are required for SmishGuard to protect you",
                    Toast.LENGTH_LONG
                ).show()
                // In a production app, you'd show a custom dialog with a
                // button to open Settings so the user can grant permissions manually.
            }
        }
    }
}
