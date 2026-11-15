package com.slimvideo.app.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object MediaStoreHelper {

    /**
     * Resolves unique output file name strictly formatted as:
     * <original_name>-1.mp4, <original_name>-2.mp4, etc.
     */
    private fun resolveUniqueDisplayName(context: Context, originalBase: String): String {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Video.Media._ID)
        val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ?"

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } catch (_: Exception) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        var index = 1
        while (index <= 100) {
            val candidate = "$originalBase-$index.mp4"
            var exists = false

            try {
                resolver.query(collection, projection, selection, arrayOf(candidate), null)?.use { cursor ->
                    exists = cursor.count > 0
                }
            } catch (_: Exception) {
                exists = false
            }

            if (!exists) {
                val moviesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "SlimVideo"
                )
                if (moviesDir.exists() && File(moviesDir, candidate).exists()) {
                    exists = true
                }
            }

            if (!exists) {
                return candidate
            }
            index++
        }

        return "$originalBase-$index.mp4"
    }

    /**
     * Save compressed video directly into Android System Gallery / Movies / SlimVideo
     * It immediately appears in Google Photos, Samsung Gallery, WhatsApp, etc.
     */
    suspend fun saveVideoToGallery(
        context: Context,
        sourceFile: File,
        baseName: String
    ): Uri = withContext(Dispatchers.IO) {
        val originalBase = baseName.substringBeforeLast(".")
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .ifBlank { "video" }

        val displayName = resolveUniqueDisplayName(context, originalBase)
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SlimVideo")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                    put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
                }

                val collection = try {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } catch (_: Exception) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }

                val uri = resolver.insert(collection, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        FileInputStream(sourceFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                    return@withContext uri
                }
            } catch (e: Exception) {
                // MediaStore insert failed, continue to standard public storage fallback
            }
        }

        // Standard public storage fallback for Android 9 or if MediaStore insert failed
        try {
            val moviesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "SlimVideo"
            )
            if (!moviesDir.exists()) moviesDir.mkdirs()

            val targetFile = File(moviesDir, displayName)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Trigger Media Scanner so it appears instantly in gallery
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf("video/mp4"),
                null
            )

            return@withContext FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            )
        } catch (_: Exception) {
            // Absolute fallback to internal cache FileProvider
            return@withContext FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                sourceFile
            )
        }
    }

    /**
     * Share video with WhatsApp, Telegram, Gmail, Drive via Android system share sheet
     */
    fun shareVideo(context: Context, videoFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                videoFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Compressed Video")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) {}
    }

    /**
     * Open and play the compressed video in the user's default video player app
     */
    fun openVideo(context: Context, uri: Uri) {
        try {
            val playIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(playIntent)
        } catch (_: Exception) {}
    }
}
