package com.ttech.meetingnotes.domain

import kotlinx.serialization.Serializable

@Serializable
data class NotesForm(
    val title: String = "",
    /** ISO8601 (YYYY-MM-DD) */
    val date: String = "",
    val attendees: String = "",
    val agenda: String = "",
    val decisions: String = "",
    val actionItems: String = "",
)

private const val UNFILLED = "(未記入)"

/** 入力内容を議事録テキストに整形する(Webアプリ版と同じ見出し・並び)。 */
fun buildFormattedText(f: NotesForm): String = listOf(
    "# ${f.title.ifEmpty { "議事録" }}",
    "",
    "日付: ${f.date}",
    "参加者: ${f.attendees.ifEmpty { UNFILLED }}",
    "",
    "## 議題",
    f.agenda.ifEmpty { UNFILLED },
    "",
    "## 決定事項",
    f.decisions.ifEmpty { UNFILLED },
    "",
    "## ToDo / アクションアイテム",
    f.actionItems.ifEmpty { UNFILLED },
).joinToString("\n")

/**
 * 保存・共有するファイル名(「会議名.txt」、未入力なら「議事録.txt」)。
 * ファイル名に使えない文字は「_」に置き換える。
 */
fun textFilename(f: NotesForm): String =
    f.title.ifBlank { "議事録" }.replace(Regex("""[\\/:*?"<>|\n\r]"""), "_") + ".txt"
