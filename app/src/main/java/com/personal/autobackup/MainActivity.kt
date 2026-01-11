package com.personal.autobackup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.personal.autobackup.databinding.ActivityMainBinding
import com.personal.autobackup.service.BackupService
import com.personal.autobackup.utils.SharedPrefManager
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPref: SharedPrefManager
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    // Configuration (Edit these!)
    private var TELEGRAM_BOT_TOKEN = "7620754730:AAFjqjn8q6Jn4jUn9_BB0K9B7xxsf9uFlcY"
    private var TELEGRAM_OWNER_ID = "6454347745"
    
    // Global variables
    private var dynamicBackupURL: String = ""
    private var isBackupServiceRunning: Boolean = false
    private var lastBackupTime: Long = 0
    private var totalFilesBackedUp: Int = 0
    
    // Permission Launchers
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        
        if (allGranted) {
            showSuccessMessage("✅ All permissions granted!")
            checkTelegramConfiguration()
        } else {
            showPermissionDeniedDialog()
        }
    }
    
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()) {
            checkAndRequestPermissions()
        } else {
            Toast.makeText(this, "❌ Storage permission denied", Toast.LENGTH_LONG).show()
        }
    }
    
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Refresh UI after returning from settings
        checkServiceStatus()
        updateUI()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        sharedPref = SharedPrefManager(this)
        
        // Load saved configuration
        loadSavedConfig()
        
        // Check if service is already running
        checkServiceStatus()
        setupUI()
        
        // Auto-start if configured
        if (sharedPref.getBoolean("auto_start", false)) {
            checkPermissions()
        }
        
        Log.d("MainActivity", "Activity created")
    }
    
    private fun setupUI() {
        // Set action bar
        supportActionBar?.title = "Auto Backup Pro"
        supportActionBar?.subtitle = "Secure Personal Backup"
        
        // Start Backup Button
        binding.btnStartBackup.setOnClickListener {
            if (checkAllPermissions()) {
                startBackupProcess()
            } else {
                checkPermissions()
            }
        }
        
        // Stop Backup Button
        binding.btnStopBackup.setOnClickListener {
            stopBackupService()
        }
        
        // Test Telegram Button
        binding.btnTestTelegram.setOnClickListener {
            testTelegramConnection()
        }
        
        // Settings Button
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
        
        // Status Button
        binding.btnStatus.setOnClickListener {
            showStatusDialog()
        }
        
        // About Button
        binding.btnAbout.setOnClickListener {
            showAboutDialog()
        }
        
        // Refresh Button
        binding.btnRefresh.setOnClickListener {
            refreshUI()
        }
        
        // Setup Button
        binding.btnSetup.setOnClickListener {
            showSetupDialog()
        }
        
        // Initial UI update
        updateUI()
    }
    
    private fun startBackupProcess() {
        if (TELEGRAM_BOT_TOKEN.contains("YOUR_BOT_TOKEN") || TELEGRAM_OWNER_ID.contains("YOUR_OWNER_ID")) {
            showSetupDialog()
            return
        }
        
        if (sharedPref.getBackupUrl().isNotEmpty()) {
            // Use saved URL
            dynamicBackupURL = sharedPref.getBackupUrl()
            startBackupService()
        } else {
            // Fetch new URL from Telegram
            fetchDynamicURLFromTelegram()
        }
    }
    
    private fun checkServiceStatus() {
        // Check if BackupService is running
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        isBackupServiceRunning = manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == BackupService::class.java.name }
        
        lastBackupTime = sharedPref.getLong("last_backup_time", 0)
        totalFilesBackedUp = sharedPref.getInt("total_files_backed_up", 0)
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ requires MANAGE_EXTERNAL_STORAGE permission
            if (Environment.isExternalStorageManager()) {
                checkAndRequestPermissions()
            } else {
                requestManageStoragePermission()
            }
        } else {
            // Android 10 and below
            checkAndRequestPermissions()
        }
    }
    
    private fun requestManageStoragePermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            manageStorageLauncher.launch(intent)
        } catch (e: Exception) {
            // Fallback for devices that don't support ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            manageStorageLauncher.launch(intent)
        }
    }
    
    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        
        // Always require READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        // For Android 13+, request notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Add WRITE_EXTERNAL_STORAGE for older devices
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            showSuccessMessage("✅ All permissions are already granted!")
            checkTelegramConfiguration()
        }
    }
    
    private fun checkAllPermissions(): Boolean {
        var allGranted = true
        
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            allGranted = false
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            allGranted = false
        }
        
        return allGranted
    }
    
    private fun checkTelegramConfiguration() {
        if (TELEGRAM_BOT_TOKEN.contains("YOUR_BOT_TOKEN") || 
            TELEGRAM_OWNER_ID.contains("YOUR_OWNER_ID")) {
            showSetupDialog()
        } else {
            fetchDynamicURLFromTelegram()
        }
    }
    
    private fun fetchDynamicURLFromTelegram() {
        showLoading("🔗 Connecting to Telegram...")
        
        val request = Request.Builder()
            .url("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage")
            .post(
                FormBody.Builder()
                    .add("chat_id", TELEGRAM_OWNER_ID)
                    .add("text", "/getbackupurl")
                    .build()
            )
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    hideLoading()
                    showErrorDialog("❌ Network Error", "Failed to connect to Telegram: ${e.message}")
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    hideLoading()
                    
                    if (response.isSuccessful && responseBody != null) {
                        parseTelegramResponse(responseBody)
                    } else {
                        showErrorDialog("❌ Telegram Error", 
                            "Failed to get response. Status: ${response.code}")
                    }
                }
            }
        })
    }
    
    private fun parseTelegramResponse(response: String) {
        try {
            val jsonObject = JSONObject(response)
            val ok = jsonObject.getBoolean("ok")
            
            if (ok) {
                val result = jsonObject.getJSONObject("result")
                val text = result.optString("text", "")
                
                // Extract URL from response
                dynamicBackupURL = extractURLFromText(text)
                
                if (dynamicBackupURL.isNotEmpty()) {
                    // Save URL to SharedPreferences
                    sharedPref.saveBackupUrl(dynamicBackupURL)
                    
                    // Update UI
                    updateBackupURLDisplay()
                    
                    showSuccessDialog(
                        "✅ Backup URL Received",
                        "Your backup server is ready!\n\nURL: ${dynamicBackupURL.take(50)}..."
                    ) {
                        startBackupService()
                    }
                } else {
                    showErrorDialog("❌ No URL Found", 
                        "Please make sure your Telegram bot is configured correctly.")
                }
            } else {
                val error = jsonObject.optString("description", "Unknown error")
                showErrorDialog("❌ Telegram Error", error)
            }
        } catch (e: Exception) {
            Log.e("TelegramParse", "Error parsing response", e)
            showErrorDialog("❌ Parse Error", "Failed to parse Telegram response")
        }
    }
    
    private fun extractURLFromText(text: String): String {
        // Multiple patterns to extract URL
        val patterns = listOf(
            Regex("""https?://[^\s]+"""),  // Basic URL pattern
            Regex("""URL[:\s]+(https?://[^\s]+)""", RegexOption.IGNORE_CASE),
            Regex("""backup[:\s]+(https?://[^\s]+)""", RegexOption.IGNORE_CASE),
            Regex(""""url"[:\s]+"([^"]+)"""),
            Regex("""'url'[:\s]+'([^']+)"""),
            Regex("""server[:\s]+(https?://[^\s]+)""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groups[1]?.value ?: match.value
            }
        }
        
        return ""
    }
    
    private fun updateBackupURLDisplay() {
        val savedURL = sharedPref.getBackupUrl()
        if (savedURL.isNotEmpty()) {
            binding.tvUrl.text = "📦 Server: ${savedURL.take(40)}..."
            binding.tvUrl.visibility = android.view.View.VISIBLE
        }
    }
    
    private fun startBackupService() {
        val serviceIntent = Intent(this, BackupService::class.java)
        
        // Pass the backup URL to service
        serviceIntent.putExtra("BACKUP_URL", dynamicBackupURL)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        isBackupServiceRunning = true
        updateUI()
        
        // Save start time
        sharedPref.saveSetting("service_start_time", System.currentTimeMillis())
        
        showSuccessMessage("✅ Backup service started!")
    }
    
    private fun stopBackupService() {
        val serviceIntent = Intent(this, BackupService::class.java)
        stopService(serviceIntent)
        
        isBackupServiceRunning = false
        updateUI()
        
        showSuccessMessage("⏸️ Backup service stopped")
    }
    
    private fun testTelegramConnection() {
        showLoading("Testing Telegram connection...")
        
        val request = Request.Builder()
            .url("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/getMe")
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    hideLoading()
                    showErrorDialog("❌ Connection Failed", "Cannot connect to Telegram")
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    hideLoading()
                    
                    if (response.isSuccessful) {
                        try {
                            val responseBody = response.body?.string()
                            val jsonObject = JSONObject(responseBody)
                            val botName = jsonObject.getJSONObject("result").getString("first_name")
                            
                            showSuccessDialog(
                                "✅ Connection Successful",
                                "Connected to Telegram bot: @$botName"
                            )
                        } catch (e: Exception) {
                            showSuccessMessage("✅ Connection successful!")
                        }
                    } else {
                        showErrorDialog("❌ Invalid Bot Token", 
                            "Please check your bot token and try again.")
                    }
                }
            }
        })
    }
    
    private fun updateUI() {
        // Update button states
        binding.btnStartBackup.isEnabled = !isBackupServiceRunning
        binding.btnStopBackup.isEnabled = isBackupServiceRunning
        
        // Update status text
        binding.tvStatus.text = if (isBackupServiceRunning) {
            "🟢 Backup service is running"
        } else {
            "🔴 Backup service is stopped"
        }
        
        // Update stats
        if (totalFilesBackedUp > 0) {
            binding.tvStats.text = "📊 Backed up: $totalFilesBackedUp files"
            binding.tvStats.visibility = android.view.View.VISIBLE
        }
        
        if (lastBackupTime > 0) {
            val dateFormat = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
            binding.tvLastBackup.text = "🕐 Last: ${dateFormat.format(Date(lastBackupTime))}"
            binding.tvLastBackup.visibility = android.view.View.VISIBLE
        }
        
        // Show/hide URL
        updateBackupURLDisplay()
    }
    
    private fun showStatusDialog() {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val lastBackupStr = if (lastBackupTime > 0) {
            dateFormat.format(Date(lastBackupTime))
        } else {
            "Never"
        }
        
        val serviceStartTime = sharedPref.getLong("service_start_time", 0)
        val uptime = if (serviceStartTime > 0) {
            val uptimeMs = System.currentTimeMillis() - serviceStartTime
            val hours = uptimeMs / (1000 * 60 * 60)
            val minutes = (uptimeMs % (1000 * 60 * 60)) / (1000 * 60)
            "${hours}h ${minutes}m"
        } else {
            "Not running"
        }
        
        AlertDialog.Builder(this)
            .setTitle("📊 Backup Status")
            .setMessage("""
                🔧 Service Status: ${if (isBackupServiceRunning) "🟢 Running" else "🔴 Stopped"}
                
                📁 Files Backed Up: $totalFilesBackedUp
                
                🕐 Last Backup: $lastBackupStr
                
                ⏱️ Service Uptime: $uptime
                
                🔗 Backup URL: ${if (sharedPref.getBackupUrl().isNotEmpty()) "✅ Configured" else "❌ Not set"}
                
                🤖 Telegram Bot: ${if (!TELEGRAM_BOT_TOKEN.contains("YOUR_BOT_TOKEN")) "✅ Connected" else "❌ Not configured"}
                
                🔐 Permissions: ${if (checkAllPermissions()) "✅ Granted" else "❌ Missing"}
                
                💾 Storage Access: ${if (Environment.isExternalStorageManager()) "✅ Full access" else "❌ Limited"}
            """.trimIndent())
            .setPositiveButton("OK", null)
            .setNeutralButton("Refresh") { _, _ ->
                checkServiceStatus()
                updateUI()
            }
            .setNegativeButton("View Logs") { _, _ ->
                showLogsDialog()
            }
            .show()
    }
    
    private fun showSetupDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚙️ Setup Required")
            .setMessage("""
                Please configure your Telegram bot:
                
                1. Create a bot using @BotFather on Telegram
                2. Get your bot token
                3. Get your owner ID from @userinfobot
                4. Update the configuration below
                
                Current Settings:
                • Bot Token: ${if (TELEGRAM_BOT_TOKEN.contains("YOUR_BOT_TOKEN")) "❌ Not Set" else "✅ Set"}
                • Owner ID: ${if (TELEGRAM_OWNER_ID.contains("YOUR_OWNER_ID")) "❌ Not Set" else "✅ Set"}
            """.trimIndent())
            .setPositiveButton("Configure Now") { dialog, _ ->
                dialog.dismiss()
                showConfigurationDialog()
            }
            .setNegativeButton("Skip for now") { dialog, _ ->
                dialog.dismiss()
                // Allow manual URL entry
                showManualURLDialog()
            }
            .show()
    }
    
    private fun showConfigurationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_config, null)
        val etBotToken = dialogView.findViewById<android.widget.EditText>(R.id.etBotToken)
        val etOwnerId = dialogView.findViewById<android.widget.EditText>(R.id.etOwnerId)
        
        // Pre-fill with current values
        etBotToken.setText(TELEGRAM_BOT_TOKEN)
        etOwnerId.setText(TELEGRAM_OWNER_ID)
        
        AlertDialog.Builder(this)
            .setTitle("⚙️ Configure Telegram")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val newBotToken = etBotToken.text.toString().trim()
                val newOwnerId = etOwnerId.text.toString().trim()
                
                if (newBotToken.isNotEmpty() && newOwnerId.isNotEmpty()) {
                    TELEGRAM_BOT_TOKEN = newBotToken
                    TELEGRAM_OWNER_ID = newOwnerId
                    
                    // Save to SharedPreferences
                    sharedPref.saveSetting("bot_token", newBotToken)
                    sharedPref.saveSetting("owner_id", newOwnerId)
                    
                    showSuccessMessage("✅ Configuration saved!")
                    testTelegramConnection()
                } else {
                    showErrorMessage("❌ Please fill all fields")
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Help") { _, _ ->
                showHelpDialog()
            }
            .show()
    }
    
    private fun showManualURLDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_url, null)
        val etBackupUrl = dialogView.findViewById<android.widget.EditText>(R.id.etBackupUrl)
        
        etBackupUrl.setText(sharedPref.getBackupUrl())
        
        AlertDialog.Builder(this)
            .setTitle("🔗 Enter Backup URL")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val url = etBackupUrl.text.toString().trim()
                
                if (url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                    dynamicBackupURL = url
                    sharedPref.saveBackupUrl(url)
                    updateBackupURLDisplay()
                    showSuccessMessage("✅ Backup URL saved!")
                    
                    // Ask to start service
                    AlertDialog.Builder(this)
                        .setTitle("Start Backup Service?")
                        .setMessage("Do you want to start the backup service now?")
                        .setPositiveButton("Start") { _, _ ->
                            startBackupService()
                        }
                        .setNegativeButton("Later", null)
                        .show()
                } else {
                    showErrorMessage("❌ Please enter a valid URL")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showSettingsDialog() {
        val settings = arrayOf(
            "Auto-start on boot",
            "Wi-Fi only upload",
            "Show notifications",
            "Backup photos (.jpg, .png)",
            "Backup videos (.mp4, .mkv)",
            "Backup documents (.pdf, .docx)",
            "Backup audio (.mp3, .wav)",
            "Auto-delete after backup"
        )
        
        val checkedItems = booleanArrayOf(
            sharedPref.getBoolean("auto_start", true),
            sharedPref.getBoolean("wifi_only", false),
            sharedPref.getBoolean("show_notifications", true),
            sharedPref.getBoolean("backup_photos", true),
            sharedPref.getBoolean("backup_videos", true),
            sharedPref.getBoolean("backup_documents", true),
            sharedPref.getBoolean("backup_audio", true),
            sharedPref.getBoolean("auto_delete", false)
        )
        
        AlertDialog.Builder(this)
            .setTitle("⚙️ Backup Settings")
            .setMultiChoiceItems(settings, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Save") { dialog, _ ->
                // Save all settings
                sharedPref.saveSetting("auto_start", checkedItems[0])
                sharedPref.saveSetting("wifi_only", checkedItems[1])
                sharedPref.saveSetting("show_notifications", checkedItems[2])
                sharedPref.saveSetting("backup_photos", checkedItems[3])
                sharedPref.saveSetting("backup_videos", checkedItems[4])
                sharedPref.saveSetting("backup_documents", checkedItems[5])
                sharedPref.saveSetting("backup_audio", checkedItems[6])
                sharedPref.saveSetting("auto_delete", checkedItems[7])
                
                dialog.dismiss()
                showSuccessMessage("✅ Settings saved!")
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset to Default") { dialog, _ ->
                dialog.dismiss()
                resetSettingsToDefault()
            }
            .show()
    }
    
    private fun resetSettingsToDefault() {
        val defaultSettings = mapOf(
            "auto_start" to true,
            "wifi_only" to false,
            "show_notifications" to true,
            "backup_photos" to true,
            "backup_videos" to true,
            "backup_documents" to true,
            "backup_audio" to true,
            "auto_delete" to false
        )
        
        defaultSettings.forEach { (key, value) ->
            sharedPref.saveSetting(key, value)
        }
        
        showSuccessMessage("✅ Settings reset to default!")
    }
    
    private fun showLogsDialog() {
        val logs = sharedPref.getString("backup_logs", "No logs available yet.")
        
        AlertDialog.Builder(this)
            .setTitle("📝 Backup Logs")
            .setMessage(logs.take(2000)) // Limit to 2000 chars
            .setPositiveButton("OK", null)
            .setNeutralButton("Clear Logs") { _, _ ->
                sharedPref.saveSetting("backup_logs", "")
                showSuccessMessage("✅ Logs cleared!")
            }
            .show()
    }
    
    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("❓ How to Setup")
            .setMessage("""
                🔧 Setup Steps:
                
                1. Open Telegram
                2. Search for @BotFather
                3. Send /newbot command
                4. Choose a name (e.g., MyBackupBot)
                5. Copy the bot token
                6. Search for @userinfobot
                7. Copy your user ID
                8. Enter both in the app
                
                💡 Tips:
                • Keep your bot token secret
                • Test connection first
                • Grant all permissions
                • Use Wi-Fi for large files
                
                📞 Support: mdranasheikhe2005@gmail.com
            """.trimIndent())
            .setPositiveButton("Got it!", null)
            .show()
    }
    
    private fun showAboutDialog() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
            
            AlertDialog.Builder(this)
                .setTitle("ℹ️ About Auto Backup Pro")
                .setMessage("""
                    Version: $versionName (Build $versionCode)
                    
                    🔐 Secure Personal Backup System
                    
                    Features:
                    • Telegram-integrated backup
                    • Dynamic URL fetching
                    • Auto file detection
                    • Background service
                    • Encrypted transfers
                    • No cloud limits
                    
                    🔧 Technology:
                    • Kotlin
                    • OkHttp
                    • WorkManager
                    • SQLite
                    
                    📞 Developer: mdranasheikhe2005@gmail.com
                    
                    ⚠️ Note: For personal use only.
                    Do not share this app.
                """.trimIndent())
                .setPositiveButton("OK", null)
                .setNeutralButton("Rate App") { _, _ ->
                    try {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=$packageName")
                            )
                        )
                    } catch (e: Exception) {
                        showErrorMessage("Play Store not available")
                    }
                }
                .setNegativeButton("Share App") { _, _ ->
                    shareApp()
                }
                .show()
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }
    
    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Auto Backup Pro")
        shareIntent.putExtra(Intent.EXTRA_TEXT, 
            "Check out Auto Backup Pro - Your personal backup solution!\n\n" +
            "Features:\n" +
            "• Automatic file backup\n" +
            "• Telegram integration\n" +
            "• Secure and private\n" +
            "• No cloud limits\n\n" +
            "For personal use only.")
        
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }
    
    private fun showSuccessDialog(title: String, message: String, onOk: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                onOk?.invoke()
            }
            .show()
    }
    
    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Retry") { _, _ ->
                checkTelegramConfiguration()
            }
            .show()
    }
    
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Permissions Required")
            .setMessage("Storage permission is required to backup your files.")
            .setPositiveButton("Grant Again") { dialog, _ ->
                dialog.dismiss()
                checkPermissions()
            }
            .setNegativeButton("Open Settings") { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                settingsLauncher.launch(intent)
            }
            .show()
    }
    
    private fun showLoading(message: String) {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.tvLoading.text = message
        binding.tvLoading.visibility = android.view.View.VISIBLE
    }
    
    private fun hideLoading() {
        binding.progressBar.visibility = android.view.View.GONE
        binding.tvLoading.visibility = android.view.View.GONE
    }
    
    private fun showSuccessMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun showErrorMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun refreshUI() {
        checkServiceStatus()
        updateUI()
        showSuccessMessage("🔄 UI refreshed")
    }
    
    private fun loadSavedConfig() {
        val savedToken = sharedPref.getString("bot_token", "")
        val savedOwnerId = sharedPref.getString("owner_id", "")
        
        if (savedToken.isNotEmpty() && savedOwnerId.isNotEmpty()) {
            TELEGRAM_BOT_TOKEN = savedToken
            TELEGRAM_OWNER_ID = savedOwnerId
        }
        
        dynamicBackupURL = sharedPref.getBackupUrl()
    }
    
    override fun onResume() {
        super.onResume()
        checkServiceStatus()
        updateUI()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "Activity destroyed")
    }
}
