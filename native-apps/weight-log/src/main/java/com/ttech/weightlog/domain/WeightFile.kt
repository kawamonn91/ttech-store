package com.ttech.weightlog.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** バックアップ・機種変更・共有に使う「メモファイル」 */
object WeightFile {
    const val FORMAT = "ttech-weightlog"
    const val VERSION = 1
    const val EXTENSION = "weightlog"
    const val MAX_BYTES = 3_000_000

    @Serializable
    private data class Envelope(val format: String = "", val version: Int = 0, val entries: List<WeightEntry>? = null, val profile: Profile? = null)

    private val pretty = Json(WeightJson) { prettyPrint = true }

    fun encode(entries: List<WeightEntry>, profile: Profile): String = pretty.encodeToString(Envelope(FORMAT, VERSION, entries, profile))

    fun fileName(): String = "weightlog.$EXTENSION"

    data class Decoded(val entries: List<WeightEntry>, val profile: Profile?)
    sealed interface Result {
        data class Ok(val decoded: Decoded) : Result
        data class Error(val message: String) : Result
    }

    /** 信用できない入力として扱い、大きさ・形式・版を確かめ、中身を整える。id は新しく振り直す */
    fun decode(text: String, newId: () -> String, nowMs: Long): Result {
        if (text.length > MAX_BYTES) return Result.Error("ファイルが大きすぎます")
        val envelope = try {
            WeightJson.decodeFromString<Envelope>(text)
        } catch (_: Exception) {
            return Result.Error("体重ノートのファイルではありません")
        }
        if (envelope.format != FORMAT || envelope.entries == null) return Result.Error("体重ノートのファイルではありません")
        if (envelope.version > VERSION) return Result.Error("新しいバージョンのアプリで作られたファイルです。アプリを更新してください")
        val clean = envelope.entries.take(Limits.MAX_ENTRIES).map { it.sanitized(nowMs).copy(id = newId()) }
        return Result.Ok(Decoded(clean, envelope.profile?.sanitized()))
    }
}
