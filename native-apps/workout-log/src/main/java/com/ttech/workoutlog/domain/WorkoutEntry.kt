package com.ttech.workoutlog.domain

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutEntry(
    val id: String,
    /** ISO8601 (YYYY-MM-DD) */
    val date: String,
    val exercise: String,
    val weightKg: Double,
    val reps: Int,
    val sets: Int,
)

/**
 * 入力値から記録を作る。種目が空なら「種目未入力」、数値が読めなければ0にする(Webアプリ版と同じ)。
 */
fun buildEntry(id: String, date: String, exercise: String, weightKg: String, reps: String, sets: String): WorkoutEntry =
    WorkoutEntry(
        id = id,
        date = date,
        exercise = exercise.trim().ifEmpty { "種目未入力" },
        weightKg = weightKg.toDoubleOrNull() ?: 0.0,
        reps = reps.toIntOrNull() ?: 0,
        sets = sets.toIntOrNull() ?: 0,
    )

/** 入力候補に出す、これまでに記録した種目名(重複なし・初めて登場した順)。 */
fun List<WorkoutEntry>.exerciseNames(): List<String> = map { it.exercise }.distinct()

/** 「40kg × 10回 × 3セット」形式。重量は整数なら小数点を付けない。 */
fun WorkoutEntry.summary(): String {
    val weight = if (weightKg == weightKg.toLong().toDouble()) weightKg.toLong().toString() else weightKg.toString()
    return "${weight}kg × ${reps}回 × ${sets}セット"
}
