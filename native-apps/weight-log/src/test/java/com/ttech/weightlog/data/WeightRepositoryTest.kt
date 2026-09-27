package com.ttech.weightlog.data

import com.ttech.weightlog.domain.Profile
import com.ttech.weightlog.domain.WeightEntry
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class WeightRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1000L
    private fun repo(dir: File, dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        WeightRepository(File(dir, "weight.json"), dispatcher) { now++ }

    private fun entry(date: String, kg: Double = 70.0) = WeightEntry(id = "id-$date", date = date, weightKg = kg)

    @Test
    fun `保存した記録は、読み直しても残っている`() = runTest {
        val d = StandardTestDispatcher(testScheduler)
        val dir = tmp.newFolder()
        val a = repo(dir, d)
        a.load()
        assertTrue(a.loaded.value)
        a.upsert(entry("2026-09-01"))
        a.upsert(entry("2026-09-02"))
        val b = repo(dir, d)
        assertFalse(b.loaded.value)
        b.load()
        assertEquals(setOf("2026-09-01", "2026-09-02"), b.entries.value.map { it.date }.toSet())
    }

    @Test
    fun `同じ日付を記録し直すと、作成日時は変えずに上書きする`() = runTest {
        val r = repo(tmp.newFolder(), StandardTestDispatcher(testScheduler))
        r.load()
        r.upsert(entry("2026-09-01", 70.0))
        val created = r.getByDate("2026-09-01")!!.createdAtMs
        r.upsert(entry("2026-09-01", 69.5))
        assertEquals(1, r.entries.value.size)
        assertEquals(69.5, r.getByDate("2026-09-01")!!.weightKg, 0.0)
        assertEquals(created, r.getByDate("2026-09-01")!!.createdAtMs)
    }

    @Test
    fun `プロフィールを保存・更新できる`() = runTest {
        val r = repo(tmp.newFolder(), StandardTestDispatcher(testScheduler))
        r.load()
        r.updateProfile { it.copy(heightCm = 170.0) }
        r.updateProfile { it.copy(goalWeightKg = 65.0) }
        assertEquals(Profile(heightCm = 170.0, goalWeightKg = 65.0), r.profile.value)
    }

    @Test
    fun `削除できる`() = runTest {
        val r = repo(tmp.newFolder(), StandardTestDispatcher(testScheduler))
        r.load()
        r.upsert(entry("2026-09-01"))
        r.upsert(entry("2026-09-02"))
        r.delete(r.getByDate("2026-09-01")!!.id)
        assertEquals(listOf("2026-09-02"), r.entries.value.map { it.date })
    }

    @Test
    fun `壊れたファイルは、消さずに退避して空から始める`() = runTest {
        val dir = tmp.newFolder()
        File(dir, "weight.json").writeText("{壊れた")
        val r = repo(dir, StandardTestDispatcher(testScheduler))
        r.load()
        assertTrue(r.loaded.value)
        assertTrue(r.entries.value.isEmpty())
        val saved = dir.listFiles()!!.map { it.name }
        assertTrue(saved.toString(), saved.any { it.startsWith("weight.json.corrupt-") })
        r.upsert(entry("2026-09-01"))
        assertEquals(listOf("2026-09-01"), r.entries.value.map { it.date })
    }

    @Test
    fun `保存の途中の一時ファイルが残らない`() = runTest {
        val dir = tmp.newFolder()
        val r = repo(dir, StandardTestDispatcher(testScheduler))
        r.load()
        r.upsert(entry("2026-09-01"))
        assertEquals(listOf("weight.json"), dir.listFiles()!!.map { it.name })
    }
}
