package com.ttech.flashcards.domain

import kotlinx.serialization.Serializable

@Serializable
data class FlashCard(
    val id: String,
    val front: String,
    val back: String,
)

fun isValidCard(front: String, back: String): Boolean = front.isNotBlank() && back.isNotBlank()

/** 次のカードのインデックス(末尾の次は先頭に戻る)。 */
fun nextIndex(current: Int, size: Int): Int {
    if (size <= 0) return 0
    return (current + 1).mod(size)
}

/** 前のカードのインデックス(先頭の前は末尾に戻る)。 */
fun prevIndex(current: Int, size: Int): Int {
    if (size <= 0) return 0
    return (current - 1).mod(size)
}
