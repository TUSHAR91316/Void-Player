package com.tushar.voidplayer

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun getDominantColor(imageBytes: ByteArray): Color {
    var dominantColor by remember(imageBytes) { mutableStateOf(Color.Unspecified) }

    LaunchedEffect(imageBytes) {
        dominantColor = withContext(Dispatchers.Default) {
            var bitmap: android.graphics.Bitmap? = null
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
                var sampleSize = 1
                while (bounds.outWidth / sampleSize > 128 || bounds.outHeight / sampleSize > 128) {
                    sampleSize *= 2
                }
                val decodedBitmap = BitmapFactory.decodeByteArray(
                    imageBytes,
                    0,
                    imageBytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize }
                ) ?: return@withContext Color.Cyan
                bitmap = decodedBitmap
                val palette = Palette.from(decodedBitmap).generate()
                val argb = palette.getVibrantColor(
                    palette.getDominantColor(Color.Cyan.toArgb())
                )
                Color(argb)
            } catch (e: Throwable) {
                Color.Cyan
            } finally {
                bitmap?.recycle()
            }
        }
    }

    return dominantColor
}
