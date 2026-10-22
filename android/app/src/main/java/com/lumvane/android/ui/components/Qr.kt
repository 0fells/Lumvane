package com.lumvane.android.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

fun qrBitmap(text: String, size: Int = 640): Bitmap? = runCatching {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.RGB_565)
    for (x in 0 until m.width) for (y in 0 until m.height) {
        bmp.setPixel(x, y, if (m.get(x, y)) Color.BLACK else Color.WHITE)
    }
    bmp
}.getOrNull()

@Composable
fun QrImage(text: String, modifier: Modifier = Modifier.size(280.dp), contentDescription: String? = null) {
    val bmp = remember(text) { qrBitmap(text) }
    if (bmp != null) Image(bmp.asImageBitmap(), contentDescription, modifier)
}
