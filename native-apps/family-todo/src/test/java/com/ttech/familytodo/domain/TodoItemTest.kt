package com.ttech.familytodo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TodoItemTest {
    private fun item(id: String, category: TodoCategory, done: Boolean = false) =
        TodoItem(id, "item-$id", done, category)

    @Test
    fun `カテゴリで絞り込める`() {
        val items = listOf(
            item("1", TodoCategory.TODO),
            item("2", TodoCategory.SHOPPING),
            item("3", TodoCategory.TODO),
        )
        assertEquals(listOf("1", "3"), items.filterByCategory(TodoCategory.TODO).map { it.id })
        assertEquals(listOf("2"), items.filterByCategory(TodoCategory.SHOPPING).map { it.id })
    }

    @Test
    fun `完了済みを除くと未完了だけが残る`() {
        val items = listOf(
            item("1", TodoCategory.TODO, done = true),
            item("2", TodoCategory.TODO, done = false),
            item("3", TodoCategory.SHOPPING, done = true),
        )
        assertEquals(listOf("2"), items.withoutDone().map { it.id })
    }

    @Test
    fun `全て未完了ならそのまま残る`() {
        val items = listOf(item("1", TodoCategory.TODO), item("2", TodoCategory.SHOPPING))
        assertEquals(2, items.withoutDone().size)
    }
}
