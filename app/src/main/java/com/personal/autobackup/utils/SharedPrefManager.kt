package com.personal.autobackup.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SharedPrefManager(private val context: Context) {
    
    private val sharedPref: SharedPreferences by lazy {
        context.getSharedPreferences("auto_backup_prefs", Context.MODE_PRIVATE)
    }
    
    private val gson = Gson()
    
    // Backup URL
    fun saveBackupUrl(url: String) {
        sharedPref.edit().putString("backup_url", url).apply()
    }
    
    fun getBackupUrl(): String {
        return sharedPref.getString("backup_url", "") ?: ""
    }
    
    // Uploaded files
    fun getUploadedFiles(): Set<String> {
        val json = sharedPref.getString("uploaded_files", "[]")
        val type = object : TypeToken<Set<String>>() {}.type
        return gson.fromJson(json, type) ?: emptySet()
    }
    
    fun addUploadedFile(fileHash: String) {
        val uploadedFiles = getUploadedFiles().toMutableSet()
        uploadedFiles.add(fileHash)
        
        val json = gson.toJson(uploadedFiles)
        sharedPref.edit().putString("uploaded_files", json).apply()
    }
    
    fun clearUploadedFiles() {
        sharedPref.edit().remove("uploaded_files").apply()
    }
    
    // Settings
    fun saveSetting(key: String, value: Any) {
        when (value) {
            is String -> sharedPref.edit().putString(key, value).apply()
            is Int -> sharedPref.edit().putInt(key, value).apply()
            is Boolean -> sharedPref.edit().putBoolean(key, value).apply()
            is Long -> sharedPref.edit().putLong(key, value).apply()
            is Float -> sharedPref.edit().putFloat(key, value).apply()
        }
    }
    
    fun getString(key: String, defaultValue: String = ""): String {
        return sharedPref.getString(key, defaultValue) ?: defaultValue
    }
    
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return sharedPref.getBoolean(key, defaultValue)
    }
    
    fun getLong(key: String, defaultValue: Long = 0): Long {
        return sharedPref.getLong(key, defaultValue)
    }
    
    // Clear all
    fun clearAll() {
        sharedPref.edit().clear().apply()
    }
}
