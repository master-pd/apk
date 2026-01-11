package com.personal.autobackup.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.personal.autobackup.AutoBackupApp
import com.personal.autobackup.MainActivity
import com.personal.autobackup.R
import com.personal.autobackup.utils.FileScanner
import com.personal.autobackup.utils.SharedPrefManager
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

class BackupService : Service() {
    
    private val TAG = "BackupService"
    private var isRunning = false
    private lateinit var fileScanner: FileScanner
    private lateinit var sharedPref: SharedPrefManager
    private val client = OkHttpClient()
    private var serviceJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        fileScanner = FileScanner(this)
        sharedPref = SharedPrefManager(this)
        
        startForegroundService()
        isRunning = true
    }
    
    private fun startForegroundService() {
        val notification = createNotification("Starting backup service...")
        startForeground(1, notification)
    }
    
    private fun createNotification(message: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, AutoBackupApp.CHANNEL_ID)
            .setContentTitle("Auto Backup Pro")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        
        val backupUrl = intent?.getStringExtra("BACKUP_URL") ?: ""
        if (backupUrl.isNotEmpty()) {
            sharedPref.saveBackupUrl(backupUrl)
            Log.d(TAG, "Backup URL received: ${backupUrl.take(50)}...")
        }
        
        // Start scanning in background
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            startBackupProcess()
        }
        
        return START_STICKY
    }
    
    private suspend fun startBackupProcess() {
        while (isRunning) {
            try {
                // Get backup URL from SharedPreferences
                val backupUrl = sharedPref.getBackupUrl()
                
                if (backupUrl.isEmpty()) {
                    updateNotification("Waiting for backup URL...")
                    delay(30000) // Wait 30 seconds
                    continue
                }
                
                updateNotification("Scanning for new files...")
                
                // Scan for new files
                val newFiles = fileScanner.getNewFiles()
                Log.d(TAG, "Found ${newFiles.size} new files")
                
                if (newFiles.isNotEmpty()) {
                    // Upload files
                    uploadFiles(newFiles, backupUrl)
                } else {
                    updateNotification("No new files found")
                }
                
                // Wait before next scan
                delay(5 * 60 * 1000) // 5 minutes
                
            } catch (e: Exception) {
                Log.e(TAG, "Backup process error: ${e.message}")
                updateNotification("Error: ${e.message?.take(30)}...")
                delay(60000) // Wait 1 minute on error
            }
        }
    }
    
    private suspend fun uploadFiles(files: List<File>, backupUrl: String) {
        files.forEach { file ->
            try {
                if (file.exists() && file.length() > 0) {
                    updateNotification("Uploading: ${file.name}")
                    
                    val success = uploadFile(file, backupUrl)
                    
                    if (success) {
                        // Mark file as uploaded
                        fileScanner.markAsUploaded(file.absolutePath)
                        Log.d(TAG, "Uploaded: ${file.name}")
                    }
                    
                    delay(1000) // Delay between uploads
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading ${file.name}: ${e.message}")
            }
        }
    }
    
    private fun uploadFile(file: File, backupUrl: String): Boolean {
        return try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                )
                .addFormDataPart("filename", file.name)
                .addFormDataPart("size", file.length().toString())
                .build()
            
            val request = Request.Builder()
                .url(backupUrl)
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            
            val isSuccess = response.isSuccessful
            Log.d(TAG, "Upload response: ${response.code} - ${response.message}")
            
            response.close()
            isSuccess
            
        } catch (e: IOException) {
            Log.e(TAG, "Upload IOException: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Upload error: ${e.message}")
            false
        }
    }
    
    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        
        isRunning = false
        serviceJob?.cancel()
        
        stopForeground(true)
        stopSelf()
        
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
