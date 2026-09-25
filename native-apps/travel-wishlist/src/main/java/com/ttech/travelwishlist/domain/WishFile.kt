package com.ttech.travelwishlist.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** バックアップ・機種変更・家族との共有に使う「メモファイル」 */
object WishFile {
    const val FORMAT = "ttech-travelwishlist"
    const val VERSION = 1
    const val EXTENSION = "tabimemo"
    const val MAX_BYTES = 3_000_000

    @Serializable
    private data class Envelope(val format: String = "", val version: Int = 0, val places: List<Place>? = null)

    private val pretty = Json(WishJson) { prettyPrint = true }

    fun encode(places: List<Place>): String = pretty.encodeToString(Envelope(FORMAT, VERSION, places))

    fun fileName(): String = "tabimemo.$EXTENSION"

    sealed interface Decoded {
        data class Ok(val places: List<Place>) : Decoded
        data class Error(val message: String) : Decoded
    }

    /** 信用できない入力として扱い、大きさ・形式・版を確かめ、中身を整える。id は新しく振り直す */
    fun decode(text: String, newId: () -> String, nowMs: Long): Decoded {
        if (text.length > WishFile.MAX_BYTES) return Decoded.Error("ファイルが大きすぎます")
        val envelope = try {
            WishJson.decodeFromString<Envelope>(text)
        } catch (_: Exception) {
            return Decoded.Error("旅メモのファイルではありません")
        }
        if (envelope.format != FORMAT || envelope.places == null) return Decoded.Error("旅メモのファイルではありません")
        if (envelope.version > VERSION) return Decoded.Error("新しいバージョンのアプリで作られたファイルです。アプリを更新してください")
        val clean = envelope.places.take(Limits.MAX_PLACES).map { it.sanitized(nowMs).copy(id = newId()) }
        return Decoded.Ok(clean)
    }
}
