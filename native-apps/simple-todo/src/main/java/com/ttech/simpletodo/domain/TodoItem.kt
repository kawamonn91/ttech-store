package com.ttech.simpletodo.domain

import kotlinx.serialization.Serializable

@Serializable
data class TodoItem(
    val id: String,
    val text: String,
    val done: Boolean,
)

/** 完了済みを除いたリスト(「完了済みを削除」の対象外を残す)。 */
fun List<TodoItem>.withoutDone(): List<TodoItem> = filter { !it.done }
