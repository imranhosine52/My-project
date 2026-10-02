package com.example.data.repository.reels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * 🛠️ ReelMediaHelper
 * রিলস ও সিরিজের ভিডিও এবং ইমেজ প্রসেসিং, কম্প্রেশন ও সাইজ ভ্যালিডেশন হেল্পার।
 */
object ReelMediaHelper {
    private const val TAG = "ReelMediaHelper"

    // 🎯 সাধারণ রিলসের লিমিট (৩ মিনিট / ৮০ এমবি)
    const val MAX_REEL_DURATION_MS = 180_000L
    const val MAX_REEL_SIZE_BYTES = 80L * 1024L * 1024L

    // 🎯 মিনি-ড্রামা সিরিজ পর্বের বর্ধিত লিমিট (১০ মিনিট / ২০০ এমবি)
    const val MAX_SERIES_DURATION_MS = 600_000L
    const val MAX_SERIES_SIZE_BYTES = 200L * 1024L * 1024L

    /**
     * ইমেজ ফাইল কম্প্রেস করে JPEG ফরম্যাটে ক্যাশ ডিরেক্টরিতে সেভ করে
     */
    fun prepareCompressedImageFile(context: Context, uri: Uri, prefix: String): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            val maxDimension = 1080
            val width = originalBitmap.width
            val height = originalBitmap.height
            val ratio = width.toFloat() / height.toFloat()

            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val resized = if (width > height) {
                    Bitmap.createScaledBitmap(originalBitmap, maxDimension, (maxDimension / ratio).toInt().coerceAtLeast(1), true)
                } else {
                    Bitmap.createScaledBitmap(originalBitmap, (maxDimension * ratio).toInt().coerceAtLeast(1), maxDimension, true)
                }
                if (resized != originalBitmap) {
                    originalBitmap.recycle()
                }
                resized
            } else {
                originalBitmap
            }

            val tempFile = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(tempFile)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.flush()
            outputStream.close()

            if (!scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }

            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Image compression error: ${e.message}")
            null
        }
    }

    /**
     * Uri থেকে ভিডিও/ফাইলের সাইজ (Bytes) বের করে
     */
    fun getFileSizeBytes(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex != -1) {
                    cursor.getLong(sizeIndex)
                } else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * ভিডিও ফাইলের আসল দৈর্ঘ্য (Duration in Milliseconds) বের করে
     */
    fun getVideoDurationMs(context: Context, uri: Uri): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            duration
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * আপলোডের জন্য টেম্পোরারি ভিডিও ফাইল প্রস্তুত করে
     */
    fun prepareTempVideoFile(context: Context, fileUri: Uri, ext: String): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(fileUri) ?: return null
            val tempFile = File(context.cacheDir, "upload_reel_${System.currentTimeMillis()}.$ext")
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Temp video creation failed: ${e.message}")
            null
        }
    }
}
