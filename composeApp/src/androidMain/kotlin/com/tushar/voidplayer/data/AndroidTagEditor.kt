package com.tushar.voidplayer.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

data class AudioTagFields(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val year: String = "",
    val genre: String = "",
    val track: String = "",
    val bpm: String = "",
    val key: String = "",
    val lyrics: String = ""
)

/**
 * Read/write common tags through SAF or MediaStore content URIs.
 *
 * jaudiotagger picks its reader from the file extension, so the extension is resolved
 * from the provider's display name or MIME type. MediaStore URIs such as
 * `content://media/external/audio/media/42` carry no extension of their own.
 *
 * Reads use [directPath] when the app can open it, which avoids copying the whole audio
 * file. Otherwise the file is copied to a private temp file.
 */
class AndroidTagEditor(private val context: Context) {

    private data class SourceInfo(val extension: String, val sizeBytes: Long)

    suspend fun read(
        uri: Uri,
        displayName: String? = null,
        directPath: String? = null,
        maxCopyBytes: Long = Long.MAX_VALUE
    ): AudioTagFields? = withContext(Dispatchers.IO) {
        val direct = directPath?.let(::File)?.takeIf { it.isFile && it.canRead() && it.extension.isNotBlank() }
        if (direct != null) {
            runCatching { readFields(direct) }.getOrNull()?.let { return@withContext it }
        }
        try {
            val source = sourceInfo(uri, displayName)
            if (source.sizeBytes > maxCopyBytes) return@withContext null
            withTemporaryAudioFile(uri, source.extension, writeBack = false) { file -> readFields(file) }
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun write(uri: Uri, fields: AudioTagFields, coverBytes: ByteArray? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = sourceInfo(uri, null)
            withTemporaryAudioFile(uri, source.extension, writeBack = true) { file ->
                val audio = AudioFileIO.read(file)
                val tag = audio.tagOrCreateAndSetDefault
                // A blank field means "remove this tag". Several formats reject empty
                // values for numeric fields such as TRACK and BPM.
                listOf(
                    FieldKey.TITLE to fields.title,
                    FieldKey.ARTIST to fields.artist,
                    FieldKey.ALBUM to fields.album,
                    FieldKey.YEAR to fields.year,
                    FieldKey.GENRE to fields.genre,
                    FieldKey.TRACK to fields.track,
                    FieldKey.BPM to fields.bpm,
                    FieldKey.KEY to fields.key,
                    FieldKey.LYRICS to fields.lyrics
                ).forEach { (key, value) ->
                    val clean = value.trim()
                    if (clean.isEmpty()) {
                        if (tag.hasField(key)) tag.deleteField(key)
                    } else {
                        tag.setField(key, clean)
                    }
                }
                if (coverBytes != null) {
                    val picture = org.jaudiotagger.tag.images.ArtworkFactory.getNew()
                    picture.binaryData = coverBytes
                    picture.mimeType = "image/jpeg"
                    picture.description = "Cover"
                    tag.deleteArtworkField()
                    tag.setField(picture)
                }
                audio.commit()
                // The original is truncated during write-back, so never write a copy that
                // can no longer be parsed.
                AudioFileIO.read(file)
            }
            true
        } catch (security: SecurityException) {
            android.util.Log.w("VoidPlayer", "The selected provider denied write access to $uri", security)
            false
        } catch (error: Throwable) {
            android.util.Log.e("VoidPlayer", "Failed writing audio tags for $uri", error)
            false
        }
    }

    private fun readFields(file: File): AudioTagFields? {
        val tag = AudioFileIO.read(file).tag ?: return null
        return AudioTagFields(
            title = tag.getFirst(FieldKey.TITLE),
            artist = tag.getFirst(FieldKey.ARTIST),
            album = tag.getFirst(FieldKey.ALBUM),
            year = tag.getFirst(FieldKey.YEAR),
            genre = tag.getFirst(FieldKey.GENRE),
            track = tag.getFirst(FieldKey.TRACK),
            bpm = tag.getFirst(FieldKey.BPM),
            key = tag.getFirst(FieldKey.KEY),
            lyrics = tag.getFirst(FieldKey.LYRICS)
        )
    }

    private fun sourceInfo(uri: Uri, knownName: String?): SourceInfo {
        var name = knownName
        var size = -1L
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (name.isNullOrBlank() && nameIndex >= 0) name = cursor.getString(nameIndex)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                    }
                }
        } catch (_: Throwable) {
        }
        val fromName = name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isValidExtension() }
        val fromMime = runCatching {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(uri))
        }.getOrNull()?.lowercase()?.takeIf { it.isValidExtension() }
        val fromPath = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isValidExtension() }
        return SourceInfo(fromName ?: fromMime ?: fromPath ?: "mp3", size)
    }

    private fun String.isValidExtension(): Boolean = length in 2..5 && all(Char::isLetterOrDigit)

    private suspend fun <T> withTemporaryAudioFile(uri: Uri, extension: String, writeBack: Boolean, action: (File) -> T): T = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("tag-edit-", ".$extension", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use(input::copyTo) }
                ?: throw java.io.FileNotFoundException("Audio source is unavailable")
            val result = action(temp)
            if (writeBack) {
                temp.inputStream().use { input ->
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output -> input.copyTo(output) }
                        ?: throw java.io.FileNotFoundException("Audio source is not writable")
                }
            }
            result
        } finally {
            temp.delete()
        }
    }
}
