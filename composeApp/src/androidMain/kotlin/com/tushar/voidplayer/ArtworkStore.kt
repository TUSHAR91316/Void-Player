package com.tushar.voidplayer

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.security.MessageDigest

/** Content-addressed, size-bounded artwork cache shared by notifications and the UI. */
object ArtworkStore {
    private const val DIRECTORY = "art_cache"
    private const val MAX_CACHE_BYTES = 50L * 1024L * 1024L
    private const val MAX_DIMENSION = 512
    private const val JPEG_QUALITY = 85

    fun store(context: Context, sourceBytes: ByteArray): Uri? {
        val jpeg = downsampleToJpeg(sourceBytes) ?: return null
        return try {
            val digest = MessageDigest.getInstance("SHA-1").digest(jpeg)
            val hash = digest.joinToString("") { "%02x".format(it) }
            val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
            val target = File(directory, "$hash.jpg")
            if (!target.exists() || target.length() == 0L) {
                val temp = File(directory, "$hash.${System.nanoTime()}.tmp")
                FileOutputStream(temp).use { it.write(jpeg) }
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
            }
            target.setLastModified(System.currentTimeMillis())
            prune(directory)
            Uri.Builder()
                .scheme("content")
                .authority("${context.packageName}.artwork")
                .appendPath("art")
                .appendPath(target.name)
                .build()
        } catch (_: Throwable) {
            null
        }
    }

    /** True while the cached file behind a URI from [store] still exists. */
    fun isAvailable(context: Context, uri: Uri): Boolean {
        val name = uri.lastPathSegment ?: return false
        return File(File(context.cacheDir, DIRECTORY), name).isFile
    }

    fun downsampleToJpeg(sourceBytes: ByteArray): ByteArray? {
        var bitmap: Bitmap? = null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            // Art from loadArt() is already a small JPEG; re-encoding it only costs CPU
            // and quality, and changes the content hash for the same picture.
            if (bounds.outMimeType == "image/jpeg" &&
                bounds.outWidth <= MAX_DIMENSION && bounds.outHeight <= MAX_DIMENSION
            ) return sourceBytes
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > MAX_DIMENSION || bounds.outHeight / sampleSize > MAX_DIMENSION) {
                sampleSize *= 2
            }
            bitmap = BitmapFactory.decodeByteArray(
                sourceBytes,
                0,
                sourceBytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
            ) ?: return null
            val output = java.io.ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) return null
            return output.toByteArray()
        } catch (_: Throwable) {
            return null
        } finally {
            bitmap?.recycle()
        }
    }

    private fun prune(directory: File) {
        try {
            val files = directory.listFiles()?.filter { it.isFile && it.extension == "jpg" } ?: return
            var total = files.sumOf { it.length() }
            if (total <= MAX_CACHE_BYTES) return
            for (file in files.sortedBy { it.lastModified() }) {
                if (total <= MAX_CACHE_BYTES) break
                val size = file.length()
                if (file.delete()) total -= size
            }
        } catch (_: Throwable) { }
    }
}

class ArtworkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Artwork is read-only")
        val context = context ?: throw FileNotFoundException("Provider is detached")
        val name = uri.lastPathSegment ?: throw FileNotFoundException("Missing artwork name")
        if (!name.matches(Regex("[0-9a-f]{40}\\.jpg"))) throw FileNotFoundException("Invalid artwork name")
        val root = File(context.cacheDir, "art_cache").canonicalFile
        val file = File(root, name).canonicalFile
        if (file.parentFile != root || !file.isFile) throw FileNotFoundException("Artwork not found")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val context = context ?: throw FileNotFoundException("Provider is detached")
        val name = uri.lastPathSegment ?: throw FileNotFoundException("Missing artwork name")
        val root = File(context.cacheDir, "art_cache").canonicalFile
        val file = File(root, name).canonicalFile
        if (!name.matches(Regex("[0-9a-f]{40}\\.jpg")) || file.parentFile != root || !file.isFile) {
            throw FileNotFoundException("Artwork not found")
        }
        val columns: List<String> = projection?.map { it } ?: listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns.toTypedArray()).apply {
            addRow(Array<Any?>(columns.size) { index ->
                when (columns[index]) {
                    OpenableColumns.DISPLAY_NAME -> file.name
                    OpenableColumns.SIZE -> file.length()
                    else -> null
                }
            })
        }
    }

    override fun getType(uri: Uri): String = "image/jpeg"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read-only provider")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read-only provider")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read-only provider")
}
