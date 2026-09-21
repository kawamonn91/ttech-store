package com.ttech.qrgenerator.domain

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

/** QRコード生成(ZXing)。ビットマップ描画はUI側に任せ、ここでは行列を返すだけにする。 */
object QrGenerator {
    /** [text] が空、または生成に失敗した場合は null。 */
    fun encode(text: String, size: Int = 280, margin: Int = 2): BitMatrix? {
        if (text.isBlank()) return null
        return try {
            QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                size,
                size,
                mapOf(EncodeHintType.MARGIN to margin),
            )
        } catch (e: WriterException) {
            null
        }
    }
}
