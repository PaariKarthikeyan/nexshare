package com.example.nexshare.util

import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.json.JSONObject

data class QRCodeData(
    val ssid: String,
    val password: String,
    val host: String,
    val port: Int,
    val deviceName: String
)

object QRCodeDecoder {

    fun decodeBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = QRCodeReader()
            val result = reader.decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            null
        }
    }

    fun parseData(raw: String): QRCodeData? {
        return try {
            val json = JSONObject(raw)
            QRCodeData(
                ssid = json.optString("ssid"),
                password = json.optString("password"),
                host = json.optString("host", "192.168.43.1"),
                port = json.optInt("port", 5000),
                deviceName = json.optString("deviceName", "NexShare Device")
            )
        } catch (e: Exception) {
            null
        }
    }
}
