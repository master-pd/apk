package com.personal.autobackup.utils

import android.content.Context
import android.os.Environment
import java.io.File
import java.security.MessageDigest
import java.util.*

class FileScanner(private val context: Context) {
    
    private val sharedPref = SharedPrefManager(context)
    
    // Folders to monitor
    private val monitoredFolders = listOf(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    )
    
    // File extensions to backup
    private val allowedExtensions = setOf(
        ".jpg", ".jpeg", ".png", ".gif", ".bmp",
        ".mp4", ".mkv", ".avi", ".mov",
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".txt",
        ".mp3", ".wav"
    )
    
    fun getNewFiles(): List<File> {
        val newFiles = mutableListOf<File>()
        val uploadedFiles = sharedPref.getUploadedFiles()
        
        monitoredFolders.forEach { folder ->
            if (folder.exists() && folder.isDirectory) {
                val files = getAllFiles(folder)
                
                files.forEach { file ->
                    val fileHash = calculateFileHash(file)
                    
                    if (fileHash !in uploadedFiles && isFileAllowed(file)) {
                        newFiles.add(file)
                    }
                }
            }
        }
        
        return newFiles
    }
    
    fun markAsUploaded(filePath: String) {
        val file = File(filePath)
        val fileHash = calculateFileHash(file)
        sharedPref.addUploadedFile(fileHash)
    }
    
    private fun getAllFiles(directory: File): List<File> {
        val files = mutableListOf<File>()
        
        if (directory.exists() && directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    files.addAll(getAllFiles(file))
                } else {
                    files.add(file)
                }
            }
        }
        
        return files
    }
    
    private fun isFileAllowed(file: File): Boolean {
        val extension = file.extension.lowercase(Locale.US)
        return ".$extension" in allowedExtensions
    }
    
    private fun calculateFileHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = file.readBytes()
            val hashBytes = digest.digest(bytes)
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "${file.name}_${file.length()}_${file.lastModified()}"
        }
    }
}
