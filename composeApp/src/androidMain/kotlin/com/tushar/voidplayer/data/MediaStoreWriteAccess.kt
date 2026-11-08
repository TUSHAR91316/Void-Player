package com.tushar.voidplayer.data

import android.net.Uri

/** Activity-backed confirmation flow for MediaStore writes on Android 11 and newer. */
object MediaStoreWriteAccess {
    @Volatile
    private var requester: (suspend (Uri) -> Boolean)? = null

    fun install(request: suspend (Uri) -> Boolean) {
        requester = request
    }

    suspend fun request(uri: Uri): Boolean = requester?.invoke(uri) ?: false
}
