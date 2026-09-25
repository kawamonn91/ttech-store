package com.ttech.tripshiori.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 他の人に渡すための「しおりファイル」。同じアプリで読み込める */
object TripFile {
    const val FORMAT = "ttech-tripshiori"
    const val VERSION = 1
    const val EXTENSION = "shiori"
    const val MAX_BYTES = 2_000_000

    @Serializable
    private data class Envelope(val format: String = "", val version: Int = 0, val trip: Trip? = null)

    private val pretty = Json(TripJson) { prettyPrint = true }

    fun encode(trip: Trip): String =
        pretty.encodeToString(Envelope(format = FORMAT, version = VERSION, trip = trip))

    fun fileName(trip: Trip): String {
        val safe = trip.title.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").trim('_').take(40).ifEmpty { "shiori" }
        return "$safe.$EXTENSION"
    }

    sealed interface Decoded {
        data class Ok(val trip: Trip) : Decoded
        data class Error(val message: String) : Decoded
    }

    /**
     * ファイルの中身を読み込む。信用できない入力として扱い、大きさ・形式・版を確かめ、
     * 中身は [sanitized] で整える。id はすべて新しく振り直す(すでにあるしおりと重ならないように)。
     */
    fun decode(text: String, newId: () -> String, nowMs: Long): Decoded {
        if (text.length > MAX_BYTES) return Decoded.Error("ファイルが大きすぎます")
        val envelope = try {
            TripJson.decodeFromString<Envelope>(text)
        } catch (_: Exception) {
            return Decoded.Error("しおりのファイルではありません")
        }
        if (envelope.format != FORMAT || envelope.trip == null) return Decoded.Error("しおりのファイルではありません")
        if (envelope.version > VERSION) return Decoded.Error("新しいバージョンのアプリで作られたファイルです。アプリを更新してください")
        val clean = envelope.trip.sanitized(nowMs)
        return Decoded.Ok(clean.withNewIds(newId))
    }

    private fun Trip.withNewIds(newId: () -> String): Trip = copy(
        id = newId(),
        items = items.map { it.copy(id = newId()) },
        packing = packing.map { it.copy(id = newId()) },
        lodgings = lodgings.map { it.copy(id = newId()) },
        contacts = contacts.map { it.copy(id = newId()) },
    )
}
