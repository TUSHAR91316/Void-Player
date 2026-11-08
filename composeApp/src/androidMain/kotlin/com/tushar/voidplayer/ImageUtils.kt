package com.tushar.voidplayer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.BitmapFactory

actual fun ByteArray.toImageBitmap(): ImageBitmap? {
    return try {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(this, 0, this.size, options)

        val maxMem = com.tushar.voidplayer.utils.maxMemoryMB
        val reqWidth = if (maxMem <= 128) 300 else if (maxMem <= 256) 500 else 800
        val reqHeight = reqWidth
        var inSampleSize = 1

        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize
        // Use RGB_565 to cut memory by 50% vs ARGB_8888
        options.inPreferredConfig = Bitmap.Config.RGB_565

        val bitmap = BitmapFactory.decodeByteArray(this, 0, this.size, options) ?: return null
        bitmap.asImageBitmap()
    } catch (e: Throwable) {
        null
    }
}
