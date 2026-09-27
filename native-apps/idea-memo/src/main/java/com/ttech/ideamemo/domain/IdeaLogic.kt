package com.ttech.ideamemo.domain

object Limits {
    const val MAX_IDEAS = 1000
    const val MAX_TITLE = 80
    const val MAX_SHORT = 140
    const val MAX_TEXT = 4000
}

// ---------------------------------------------------------------- 絞り込みと並べ替え

enum class SortOrder(val label: String) {
    PRIORITY("作りたい順"),
    NEWEST("追加が新しい順"),
    UPDATED("更新が新しい順"),
    NAME("名前順"),
}

fun filterIdeas(ideas: List<Idea>, query: String, status: Status?, category: Category?): List<Idea> {
    val q = query.trim().lowercase()
    return ideas.filter { i ->
        (status == null || i.status == status) &&
            (category == null || i.category == category) &&
            (q.isEmpty() || listOf(i.title, i.oneLiner, i.memo, i.reference).any { it.lowercase().contains(q) })
    }
}

fun sortIdeas(ideas: List<Idea>, order: SortOrder): List<Idea> = when (order) {
    SortOrder.PRIORITY -> ideas.sortedWith(compareByDescending<Idea> { it.priority }.thenByDescending { it.createdAtMs })
    SortOrder.NEWEST -> ideas.sortedByDescending { it.createdAtMs }
    SortOrder.UPDATED -> ideas.sortedByDescending { it.updatedAtMs }
    SortOrder.NAME -> ideas.sortedBy { it.title }
}

// ---------------------------------------------------------------- 統計

data class Stats(val total: Int, val byStatus: Map<Status, Int>, val byCategory: Map<Category, Int>)

fun computeStats(ideas: List<Idea>): Stats = Stats(
    total = ideas.size,
    byStatus = ideas.groupingBy { it.status }.eachCount(),
    byCategory = ideas.groupingBy { it.category }.eachCount(),
)

// ---------------------------------------------------------------- 整形(保存・読み込みしても安全な形に)

fun Idea.sanitized(nowMs: Long = updatedAtMs): Idea {
    fun String.clip(max: Int) = trim().take(max)
    return copy(
        id = id.clip(80),
        title = title.clip(Limits.MAX_TITLE).ifEmpty { "(無題)" },
        oneLiner = oneLiner.clip(Limits.MAX_SHORT),
        memo = memo.clip(Limits.MAX_TEXT),
        category = category,
        status = status,
        priority = priority.coerceIn(1, 3),
        reference = reference.clip(Limits.MAX_SHORT),
        updatedAtMs = nowMs,
    )
}
