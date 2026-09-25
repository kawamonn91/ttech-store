package com.ttech.travelwishlist.data

import com.ttech.travelwishlist.domain.Place
import com.ttech.travelwishlist.domain.Status
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
class WishRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1000L
    private fun repo(dir: File, dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        WishRepository(File(dir, "places.json"), dispatcher) { now++ }

    private fun place(id: String) = Place(id = id, name = id, region = "京都府")

    @Test
    fun `保存した場所は、読み直しても残っている`() = runTest {
        val d = StandardTestDispatcher(testScheduler)
        val dir = tmp.newFolder()
        val a = repo(dir, d)
        a.load()
        assertTrue(a.loaded.value)
        a.upsert(place("a"))
        a.upsert(place("b"))
        val b = repo(dir, d)
        assertFalse(b.loaded.value)
        b.load()
        assertEquals(setOf("a", "b"), b.places.value.map { it.id }.toSet())
    }

    @Test
    fun `同じidは置き換える。新規は作成日時が入る`() = runTest {
        val r = repo(tmp.newFolder(), StandardTestDispatcher(testScheduler))
        r.load()
        r.upsert(place("a"))
        val created = r.get("a")!!.createdAtMs
        assertTrue(created > 0)
        r.upsert(r.get("a")!!.copy(name = "更新", status = Status.PLANNING))
        assertEquals(1, r.places.value.size)
        assertEquals("更新", r.get("a")!!.name)
        assertEquals(created, r.get("a")!!.createdAtMs) // 作成日時は変わらない
        assertEquals(Status.PLANNING, r.get("a")!!.status)
    }

    @Test
    fun `まとめて追加・削除できる`() = runTest {
        val r = repo(tmp.newFolder(), StandardTestDispatcher(testScheduler))
        r.load()
        r.addAll(listOf(place("a"), place("b"), place("c")))
        r.delete("b")
        assertEquals(listOf("a", "c"), r.places.value.map { it.id })
    }

    @Test
    fun `壊れたファイルは、消さずに退避して空から始める`() = runTest {
        val dir = tmp.newFolder()
        File(dir, "places.json").writeText("{壊れた")
        val r = repo(dir, StandardTestDispatcher(testScheduler))
        r.load()
        assertTrue(r.loaded.value)
        assertTrue(r.places.value.isEmpty())
        val saved = dir.listFiles()!!.map { it.name }
        assertTrue(saved.toString(), saved.any { it.startsWith("places.json.corrupt-") })
        r.upsert(place("a"))
        assertEquals(listOf("a"), r.places.value.map { it.id })
    }

    @Test
    fun `保存の途中の一時ファイルが残らない`() = runTest {
        val dir = tmp.newFolder()
        val r = repo(dir, StandardTestDispatcher(testScheduler))
        r.load()
        r.upsert(place("a"))
        assertEquals(listOf("places.json"), dir.listFiles()!!.map { it.name })
    }
}
