package com.ttech.familytodo.domain

import kotlinx.serialization.Serializable

enum class TodoCategory(val label: String) {
    TODO("ToDo"),
    SHOPPING("買い物リスト"),
}

@Serializable
data class TodoItem(
    val id: String,
    val text: String,
    val done: Boolean,
    val category: TodoCategory,
)

fun List<TodoItem>.filterByCategory(category: TodoCategory): List<TodoItem> = filter { it.category == category }

/** 完了済みを除いたリスト(「完了済みを削除」の対象外を残す)。 */
fun List<TodoItem>.withoutDone(): List<TodoItem> = filter { !it.done }
