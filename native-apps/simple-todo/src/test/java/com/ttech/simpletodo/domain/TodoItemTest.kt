package com.ttech.simpletodo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TodoItemTest {
    private fun item(id: String, done: Boolean = false) = TodoItem(id, "item-$id", done)

    @Test
    fun `完了済みを除くと未完了だけが残る`() {
        val items = listOf(item("1", done = true), item("2", done = false), item("3", done = true))
        assertEquals(listOf("2"), items.withoutDone().map { it.id })
    }

    @Test
    fun `全て未完了ならそのまま残る`() {
        val items = listOf(item("1"), item("2"))
        assertEquals(2, items.withoutDone().size)
    }

    @Test
    fun `空リストはそのまま空`() {
        assertEquals(0, emptyList<TodoItem>().withoutDone().size)
    }
}
