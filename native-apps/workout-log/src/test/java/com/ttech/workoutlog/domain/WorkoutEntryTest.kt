package com.ttech.workoutlog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutEntryTest {
    @Test
    fun `入力値から記録を作る`() {
        val e = buildEntry("1", "2026-09-24", " スクワット ", "60.5", "8", "3")
        assertEquals(WorkoutEntry("1", "2026-09-24", "スクワット", 60.5, 8, 3), e)
    }

    @Test
    fun `種目が空なら種目未入力になり読めない数値は0になる`() {
        val e = buildEntry("1", "2026-09-24", "  ", "", "abc", "")
        assertEquals("種目未入力", e.exercise)
        assertEquals(0.0, e.weightKg, 0.0)
        assertEquals(0, e.reps)
        assertEquals(0, e.sets)
    }

    @Test
    fun `種目名の候補は重複なしで初めて登場した順`() {
        val entries = listOf("ベンチプレス", "スクワット", "ベンチプレス", "デッドリフト").mapIndexed { i, name ->
            WorkoutEntry("$i", "2026-09-24", name, 40.0, 10, 3)
        }
        assertEquals(listOf("ベンチプレス", "スクワット", "デッドリフト"), entries.exerciseNames())
    }

    @Test
    fun `概要は重量が整数なら小数点を付けない`() {
        assertEquals("40kg × 10回 × 3セット", WorkoutEntry("1", "d", "x", 40.0, 10, 3).summary())
        assertEquals("42.5kg × 8回 × 2セット", WorkoutEntry("1", "d", "x", 42.5, 8, 2).summary())
    }
}
