package com.tushar.voidplayer

import android.Manifest
import android.content.Intent
import android.provider.MediaStore
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.mutableStateOf
import androidx.activity.result.ActivityResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val app = VoidPlayerApp.instance
        var pendingWriteResult: kotlinx.coroutines.CancellableContinuation<Boolean>? = null
        val mediaStoreWriteLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result: ActivityResult ->
            pendingWriteResult?.let { continuation ->
                pendingWriteResult = null
                if (continuation.isActive) continuation.resume(result.resultCode == RESULT_OK)
            }
        }
        com.tushar.voidplayer.data.MediaStoreWriteAccess.install { uri ->
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { continuation ->
                    pendingWriteResult?.let { previous ->
                        if (previous.isActive) previous.resume(false)
                    }
                    pendingWriteResult = continuation
                    continuation.invokeOnCancellation {
                        if (pendingWriteResult === continuation) pendingWriteResult = null
                    }
                    try {
                        val request = MediaStore.createWriteRequest(contentResolver, listOf(uri))
                        mediaStoreWriteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    } catch (error: Throwable) {
                        pendingWriteResult = null
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            }
        }
        val sharedPref = getSharedPreferences("VoidPlayerPrefs", android.content.Context.MODE_PRIVATE)
        val savedUri = sharedPref.getString("last_folder_uri", null)
        
        val pickedFolderUri = mutableStateOf(savedUri)
        val statusMsg = mutableStateOf(if (savedUri != null) "Restored Folder" else "Ready")
        val importedM3u = mutableStateOf<M3uImportData?>(null)
        var pendingM3uExport: String? = null
        val m3uOpenLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    val contents = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    if (contents != null) {
                        // The document URI's last segment is a provider ID, not the file name.
                        val displayName = runCatching {
                            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                        }.getOrNull()
                        importedM3u.value = M3uImportData(
                            displayName?.takeIf { it.isNotBlank() }
                                ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
                                ?: "Imported Playlist.m3u8",
                            contents
                        )
                    }
                } catch (error: Throwable) {
                    Toast.makeText(this, "Could not read playlist file", Toast.LENGTH_LONG).show()
                }
            }
        }
        val m3uSaveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
            if (uri != null) {
                try {
                    val contents = pendingM3uExport ?: "#EXTM3U\n"
                    contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(contents) }
                    Toast.makeText(this, "Playlist exported", Toast.LENGTH_SHORT).show()
                } catch (error: Throwable) {
                    Toast.makeText(this, "Could not write playlist file", Toast.LENGTH_LONG).show()
                } finally {
                    pendingM3uExport = null
                }
            }
        }
        val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val libraryPermissionGranted = mutableStateOf(checkSelfPermission(audioPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
           val audioGranted = permissions[if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
           libraryPermissionGranted.value = audioGranted
           if(!audioGranted) {
               statusMsg.value = "Storage Permission Denied"
           }
        }
        
        val folderPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    pickedFolderUri.value = uri.toString()
                    sharedPref.edit().putString("last_folder_uri", uri.toString()).apply()
                    statusMsg.value = "Folder Saved"
                } catch(e: Exception) { 
                    statusMsg.value = "Folder Auth Failed"
                }
            }
        }

        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        permissionLauncher.launch(permissions.toTypedArray())

        // Apply the saved theme before the first frame so Classic users never see Graphite flash.
        com.tushar.voidplayer.ui.theme.VoidTheme.mode =
            com.tushar.voidplayer.ui.theme.VoidThemeMode.fromKey(app.repository.uiTheme())

        setContent {
            App(
                repository = app.repository,
                player = app.player,
                pickedFolderUri = pickedFolderUri,
                statusMessage = statusMsg.value,
                libraryPermissionGranted = libraryPermissionGranted,
                onPickFolder = { 
                    folderPickerLauncher.launch(null)
                },
                onPickM3uImport = { m3uOpenLauncher.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain", "*/*")) },
                onExportM3uFile = { fileName, contents ->
                    pendingM3uExport = contents
                    m3uSaveLauncher.launch(fileName)
                },
                importedM3u = importedM3u,
                onConsumeM3uImport = { importedM3u.value = null }
            )
        }
    }
}
