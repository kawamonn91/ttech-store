package com.ttech.tutorprogress.domain

import kotlinx.serialization.Serializable

@Serializable
data class ProgressNote(
    val id: String,
    /** ISO8601 (YYYY-MM-DD) */
    val date: String,
    val content: String,
)

@Serializable
data class Student(
    val id: String,
    val name: String,
    /** 新しい記録が先頭 */
    val notes: List<ProgressNote> = emptyList(),
)

/** 生徒を末尾に追加する(Webアプリ版と同じく追加順に並ぶ)。 */
fun List<Student>.addStudent(student: Student): List<Student> = this + student

fun List<Student>.removeStudent(id: String): List<Student> = filterNot { it.id == id }

/** 指定した生徒の記録の先頭に追加する。内容が空なら何もしない。内容は前後の空白を除く。 */
fun List<Student>.addNote(studentId: String, note: ProgressNote): List<Student> {
    if (note.content.isBlank()) return this
    val trimmed = note.copy(content = note.content.trim())
    return map { if (it.id == studentId) it.copy(notes = listOf(trimmed) + it.notes) else it }
}
