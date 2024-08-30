package com.example.spotifyplayback

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * CacheManager class to manage the cache size and clear the cache.
 * @param context The context of the application
 */
class CacheManager(private val context:Context) {

    /**
     * getCacheSize function.
     * This function returns the size of the cache in the device.
     * @return The size of the cache in the device.
     */
    fun getCacheSize(): String {
        // Get the size of the cache directory
        val cacheDir = context.cacheDir
        val size = getFolderSize(cacheDir)

        // Convert the size to KB, MB, or GB based on the size and return
        return if (size < 1e6) {
            String.format(Locale.getDefault(), "%.2f KB", size / 1e3)
        } else if (size < 1e9) {
            String.format(Locale.getDefault(), "%.2f MB", size / 1e6)
        } else {
            String.format(Locale.getDefault(), "%.2f GB", size / 1e9)
        }
    }

    /**
     * getFolderSize function.
     * This function returns the size of a folder.
     * It uses a recursive approach to calculate the size of the folder.
     * @param dir The folder to calculate the size.
     * @return The size of the folder.
     */
    private fun getFolderSize(dir: File): Long {
        // Calculate the size of the folder recursively
        var size: Long = 0 // Initialize the size to 0

        // Iterate through the files in the directory
        for (file in dir.listFiles()!!) {
            // Add the size of the file to the total size
            size += if (file.isFile) {
                file.length()
            } else {
                // If the file is a directory, calculate the size of the directory recursively
                getFolderSize(file)
            }
        }

        // Return the total size of the folder
        return size
    }

    /**
     * clearCache function.
     * This function clears the cache in the device.
     * It deletes all the files in the cache directory.
     */
    fun clearCache() {
        // Clear the cache directory by deleting all the files recursively
        val cacheDir = context.cacheDir
        clearFolder(cacheDir)
    }

    /**
     * clearFolder function.
     * This function clears a folder by deleting all its files.
     * It uses a recursive approach to delete all the files in the folder.
     * @param dir The folder to clear.
     */
    private fun clearFolder(dir: File) {
        // Clear the folder recursively
        if (dir.isDirectory) {
            // Iterate through the files in the directory
            for (file in dir.listFiles()!!) {
                clearFolder(file)
            }
        }

        // Delete also the parent directory after deleting all the files
        dir.delete()
    }
}
