package com.ttech.roomcheckin.domain

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.serialization.Serializable

@Serializable
data class Room(
    val id: String,
    val name: String,
    /** 使用中の人の名前。空室ならnull */
    val occupiedBy: String? = null,
    /** チェックインした時刻(epoch ms)。空室ならnull */
    val checkedInAt: Long? = null,
)

/** 会議室・座席を末尾に追加する(Webアプリ版と同じく登録順に並ぶ)。 */
fun List<Room>.addRoom(room: Room): List<Room> = this + room

fun List<Room>.removeRoom(id: String): List<Room> = filterNot { it.id == id }

/** チェックインする。利用者名が空なら「利用者」とする(Webアプリ版と同じ)。 */
fun List<Room>.checkIn(id: String, who: String, now: Long): List<Room> =
    map { if (it.id == id) it.copy(occupiedBy = who.trim().ifEmpty { "利用者" }, checkedInAt = now) else it }

fun List<Room>.checkOut(id: String): List<Room> =
    map { if (it.id == id) it.copy(occupiedBy = null, checkedInAt = null) else it }

/** 状態の表示(「山田が使用中」/「空室」)。 */
fun Room.statusLabel(): String = occupiedBy?.let { "${it}が使用中" } ?: "空室"

/** ドアや座席に掲示するQRコードの中身(Webアプリ版と同じ「room:会議室名」)。 */
fun Room.qrContent(): String = "room:$name"

/**
 * QRコードの行列を作る(描画はUI側)。生成に失敗した場合は null。
 * ZXing の既定の文字コードは ISO-8859-1 で日本語の会議室名が化けるため、UTF-8 を指定する
 * (Webアプリ版の qrcode ライブラリと同じ)。
 */
fun encodeQr(text: String, size: Int = 200, margin: Int = 2): BitMatrix? = try {
    QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to margin, EncodeHintType.CHARACTER_SET to "UTF-8"),
    )
} catch (e: WriterException) {
    null
}
