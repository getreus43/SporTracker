package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object FileExporter {
    /**
     * Exposes a generic utility to save text data (XML-based GPX or KML) to the device's public Downloads directory.
     */
    fun saveTextFileToDownloads(
        context: Context,
        fileName: String,
        fileContent: String,
        mimeType: String
    ): Uri? {
        val resolver = context.contentResolver
        
        // Ensure standard clean filename endings
        val cleanedFileName = if (mimeType == "application/vnd.google-earth.kml+xml" && !fileName.endsWith(".kml")) {
            "$fileName.kml"
        } else if (mimeType == "application/gpx+xml" && !fileName.endsWith(".gpx")) {
            "$fileName.gpx"
        } else {
            fileName
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, cleanedFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RutasGPX")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        // Selection of storage Uri based on API level
        val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }

        var uri: Uri? = null
        try {
            uri = resolver.insert(contentUri, contentValues)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (uri == null) {
            // Direct write fallback for older API versions or when insert fails
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val subDir = File(downloadsDir, "RutasGPX")
                if (!subDir.exists()) subDir.mkdirs()
                val targetFile = File(subDir, cleanedFileName)
                FileOutputStream(targetFile).use { fos ->
                    fos.write(fileContent.toByteArray())
                }
                return Uri.fromFile(targetFile)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        if (uri != null) {
            try {
                resolver.openOutputStream(uri).use { outputStream ->
                    if (outputStream != null) {
                        outputStream.write(fileContent.toByteArray())
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                return uri
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }
}
