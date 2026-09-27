package com.ttech.ideamemo.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** バックアップ・機種変更・共有に使う「メモファイル」 */
object IdeaFile {
    const val FORMAT = "ttech-ideamemo"
    const val VERSION = 1
    const val EXTENSION = "ideamemo"
    const val MAX_BYTES = 3_000_000

    @Serializable
    private data class Envelope(val format: String = "", val version: Int = 0, val ideas: List<Idea>? = null)

    private val pretty = Json(IdeaJson) { prettyPrint = true }

    fun encode(ideas: List<Idea>): String = pretty.encodeToString(Envelope(FORMAT, VERSION, ideas))

    fun fileName(): String = "ideamemo.$EXTENSION"

    sealed interface Decoded {
        data class Ok(val ideas: List<Idea>) : Decoded
        data class Error(val message: String) : Decoded
    }

    /** 信用できない入力として扱い、大きさ・形式・版を確かめ、中身を整える。id は新しく振り直す */
    fun decode(text: String, newId: () -> String, nowMs: Long): Decoded {
        if (text.length > MAX_BYTES) return Decoded.Error("ファイルが大きすぎます")
        val envelope = try {
            IdeaJson.decodeFromString<Envelope>(text)
        } catch (_: Exception) {
            return Decoded.Error("アイデア帳のファイルではありません")
        }
        if (envelope.format != FORMAT || envelope.ideas == null) return Decoded.Error("アイデア帳のファイルではありません")
        if (envelope.version > VERSION) return Decoded.Error("新しいバージョンのアプリで作られたファイルです。アプリを更新してください")
        val clean = envelope.ideas.take(Limits.MAX_IDEAS).map { it.sanitized(nowMs).copy(id = newId()) }
        return Decoded.Ok(clean)
    }
}
