package com.ttech.tripshiori.data

import com.ttech.tripshiori.domain.ScheduleItem
import com.ttech.tripshiori.domain.Trip
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
class TripRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1000L
    private fun repo(dir: File, dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        TripRepository(File(dir, "trips.json"), dispatcher) { now++ }

    private fun trip(id: String, title: String = id) = Trip(id = id, title = title, startDate = "2026-10-10", endDate = "2026-10-11")

    @Test
    fun `保存したしおりは、読み直しても残っている`() = runTest {
        val d = StandardTestDispatcher(testScheduler)
        val dir = tmp.newFolder()
        val a = repo(dir, d)
        a.load()
        assertTrue(a.loaded.value)
        a.add(trip("a"))
        a.add(trip("b"))
        val b = repo(dir, d)
        assertFalse(b.loaded.value)
        b.load()
        assertEquals(setOf("a", "b"), b.trips.value.map { it.id }.toSet())
    }

    @Test
    fun `更新した順(新しい順)に並ぶ`() = runTest {
        val r = repo(tmp.newFolder(), StandardTestDispatcher(testScheduler))
        r.load()
        r.add(trip("a"))
        r.add(trip("b"))
        r.add(trip("c"))
        assertEquals(listOf("c", "b", "a"), r.trips.value.map { it.id })
        r.updateTrip("a") { it.copy(title = "更新した") }
        assertEquals(listOf("a", "c", "b"), r.trips.value.map { it.id })
        assertEquals("更新した", r.get("a")?.title)
    }

    @Test
    fun `更新は整形される(範囲外の日・長すぎる文字)`() = runTest {
        val r = repo(tmp.newFolder(), StandardTestDispatcher(testScheduler))
        r.load()
        r.add(trip("a"))
        r.updateTrip("a") { it.copy(items = listOf(ScheduleItem("i", day = 50, title = "x".repeat(300)))) }
        val item = r.get("a")!!.items.single()
        assertEquals(1, item.day)
        assertEquals(100, item.title.length)
    }

    @Test
    fun `削除できる。存在しないidの更新は何もしない`() = runTest {
        val r = repo(tmp.newFolder(), StandardTestDispatcher(testScheduler))
        r.load()
        r.add(trip("a"))
        r.updateTrip("zzz") { it.copy(title = "x") }
        assertEquals(listOf("a"), r.trips.value.map { it.id })
        r.delete("a")
        assertTrue(r.trips.value.isEmpty())
    }

    @Test
    fun `壊れたファイルは、消さずに退避して空から始める`() = runTest {
        val dir = tmp.newFolder()
        File(dir, "trips.json").writeText("{壊れた")
        val r = repo(dir, StandardTestDispatcher(testScheduler))
        r.load()
        assertTrue(r.loaded.value)
        assertTrue(r.trips.value.isEmpty())
        val saved = dir.listFiles()!!.map { it.name }
        assertTrue(saved.toString(), saved.any { it.startsWith("trips.json.corrupt-") })
        assertEquals("{壊れた", File(dir, saved.first { it.startsWith("trips.json.corrupt-") }).readText())
        // その後の保存は普通にできる
        r.add(trip("a"))
        assertEquals(listOf("a"), r.trips.value.map { it.id })
    }

    @Test
    fun `保存の途中の一時ファイルが残らない`() = runTest {
        val dir = tmp.newFolder()
        val r = repo(dir, StandardTestDispatcher(testScheduler))
        r.load()
        r.add(trip("a"))
        assertEquals(listOf("trips.json"), dir.listFiles()!!.map { it.name })
    }
}
