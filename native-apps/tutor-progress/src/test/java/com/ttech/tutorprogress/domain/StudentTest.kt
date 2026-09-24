package com.ttech.tutorprogress.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StudentTest {
    private val note = ProgressNote("n1", "2026-09-24", " 二次関数 理解度◎ ")

    @Test
    fun `生徒は追加順に並び削除できる`() {
        val students = emptyList<Student>().addStudent(Student("1", "田中")).addStudent(Student("2", "鈴木"))
        assertEquals(listOf("田中", "鈴木"), students.map { it.name })
        assertEquals(listOf("2"), students.removeStudent("1").map { it.id })
    }

    @Test
    fun `記録は指定した生徒の先頭に内容を整えて入る`() {
        val students = listOf(Student("1", "田中", listOf(ProgressNote("n0", "2026-09-17", "前回"))), Student("2", "鈴木"))
        val next = students.addNote("1", note)
        assertEquals(listOf("n1", "n0"), next[0].notes.map { it.id })
        assertEquals("二次関数 理解度◎", next[0].notes[0].content)
        assertEquals(emptyList<ProgressNote>(), next[1].notes)
    }

    @Test
    fun `内容が空の記録は追加しない`() {
        val students = listOf(Student("1", "田中"))
        assertEquals(students, students.addNote("1", note.copy(content = "  ")))
    }
}
