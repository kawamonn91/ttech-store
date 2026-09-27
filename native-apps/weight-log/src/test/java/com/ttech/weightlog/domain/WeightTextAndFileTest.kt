package com.ttech.weightlog.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightTextAndFileTest {
    private var seq = 0
    private fun newId() = "id${++seq}"

    private val entries = listOf(
        WeightEntry(id = "a", date = "2026-09-01", weightKg = 70.0, bodyFatPercent = 24.0),
        WeightEntry(id = "b", date = "2026-09-30", weightKg = 68.0, bodyFatPercent = 22.5, memo = "調子いい"),
    )
    private val profile = Profile(heightCm = 175.0, goalWeightKg = 65.0)

    @Test
    fun `直近の記録の共有テキスト`() {
        val s = computeStats(entries, profile, LocalDate.of(2026, 9, 30))
        val text = weightSummaryText(s, profile)
        assertTrue(text.contains("【体重記録】2026-09-30 68.0 kg"))
        assertTrue(text.contains("体脂肪 22.5%"))
        assertTrue(text.contains("開始から -2.0 kg"))
        assertTrue(text.contains("BMI 22.2(普通体重)"))
        assertTrue(text.contains("目標 65.0 kg(あと 3.0 kg)"))
    }

    @Test
    fun `目標を達成していれば、その旨を出す`() {
        val achieved = listOf(WeightEntry(id = "a", date = "2026-09-01", weightKg = 70.0), WeightEntry(id = "b", date = "2026-09-30", weightKg = 64.0))
        val s = computeStats(achieved, profile, LocalDate.of(2026, 9, 30))
        assertTrue(weightSummaryText(s, profile).contains("目標 65.0 kg(あと 1.0 kg 達成!)"))
    }

    @Test
    fun `記録が無ければ、その旨だけ出す`() {
        val s = computeStats(emptyList(), profile, LocalDate.of(2026, 9, 30))
        assertEquals("まだ記録がありません", weightSummaryText(s, profile))
    }

    @Test
    fun `一覧の共有テキストは新しい順`() {
        val expected = "【体重の記録】2件\n2026-09-30  68.0 kg  体脂肪22.5%  調子いい\n2026-09-01  70.0 kg  体脂肪24.0%\n"
        assertEquals(expected, entriesText(entries))
    }

    @Test
    fun `サンプルは日付が新しく、直近30日以内で、体重が緩やかに減っていく`() {
        val today = LocalDate.of(2026, 9, 30)
        val samples = sampleEntries(today, ::newId, nowMs = 1000)
        assertTrue(samples.isNotEmpty())
        assertEquals(samples.size, samples.map { it.id }.toSet().size)
        assertEquals(samples, samples.map { it.sanitized(nowMs = 1000) })
        val sorted = sortedByDateAsc(samples)
        assertTrue(sorted.first().weightKg > sorted.last().weightKg)
        assertTrue(parseDateOrNull(sorted.last().date)!! <= today)
        assertTrue(parseDateOrNull(sorted.first().date)!! >= today.minusDays(30))
    }

    @Test
    fun `メモファイルを書き出して読み込むと、中身は同じで、idは新しくなる`() {
        val decoded = (WeightFile.decode(WeightFile.encode(entries, profile), ::newId, nowMs = 99) as WeightFile.Result.Ok).decoded
        assertEquals(entries.map { it.date }, decoded.entries.map { it.date })
        assertEquals(entries.map { it.copy(id = "", updatedAtMs = 0) }, decoded.entries.map { it.copy(id = "", updatedAtMs = 0) })
        assertEquals(2, decoded.entries.map { it.id }.toSet().size)
        assertFalse(decoded.entries.any { it.id in setOf("a", "b") })
        assertEquals(profile, decoded.profile)
    }

    @Test
    fun `体重ノートのファイルでないものは断る`() {
        for (text in listOf("hello", "{}", "", """{"format":"other","version":1,"entries":[]}""", """{"format":"ttech-weightlog","version":1}""")) {
            assertEquals(text, "体重ノートのファイルではありません", (WeightFile.decode(text, ::newId, 1) as WeightFile.Result.Error).message)
        }
    }

    @Test
    fun `新しい版・大きすぎるファイルは断る`() {
        val newer = WeightFile.encode(entries, profile).replace("\"version\": 1", "\"version\": 2")
        assertTrue((WeightFile.decode(newer, ::newId, 1) as WeightFile.Result.Error).message.contains("アプリを更新"))
        assertEquals("ファイルが大きすぎます", (WeightFile.decode("x".repeat(WeightFile.MAX_BYTES + 1), ::newId, 1) as WeightFile.Result.Error).message)
    }

    @Test
    fun `知らない項目があっても読める。中身は整えられる`() {
        val text = """
            {"format":"ttech-weightlog","version":1,"extra":true,"entries":[
              {"id":"x","date":"2026-09-01","weightKg":9999,"photo":"a.png"}
            ]}
        """.trimIndent()
        val decoded = (WeightFile.decode(text, ::newId, 1) as WeightFile.Result.Ok).decoded
        assertEquals(Limits.WEIGHT_RANGE.endInclusive, decoded.entries.single().weightKg, 0.0)
    }

    @Test
    fun `読み込む件数には上限がある`() {
        val many = List(Limits.MAX_ENTRIES + 50) { entries[0].copy(id = "p$it", date = LocalDate.of(2020, 1, 1).plusDays(it.toLong()).toString()) }
        val decoded = (WeightFile.decode(WeightFile.encode(many, Profile()), ::newId, 1) as WeightFile.Result.Ok).decoded
        assertEquals(Limits.MAX_ENTRIES, decoded.entries.size)
    }
}
