package com.tushar.voidplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
expect fun getDominantColor(imageBytes: ByteArray): Color
