package com.ttech.ideamemo.data

import com.ttech.ideamemo.domain.Idea
import com.ttech.ideamemo.domain.Status
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
class IdeaRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1000L
    private fun repo(dir: File, dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        IdeaRepository(File(dir, "ideas.json"), dispatcher) { now++ }

    private fun idea(id: String) = Idea(id = id, title = id)

    @Test
    fun `保存したアイデアは、読み直しても残っている`() = runTest {
        val d = StandardTestDispatcher(testScheduler)
        val dir = tmp.newFolder()
        val a = repo(dir, d)
        a.load()
        assertTrue(a.loaded.value)
        a.upsert(idea("a"))
        a.upsert(idea("b"))
        val b = repo(dir, d)
        assertFalse(b.loaded.value)
        b.load()
        assertEquals(setOf("a", "b"), b.ideas.value.map { it.id }.toSet())
    }

    @Test
    fun `同じidは置き換える。新規は作成日時が入る`() = runTest {
        val r = repo(tmp.newFolder(), StandardTestDispatcher(testScheduler))
        r.load()
        r.upsert(idea("a"))
        val created = r.get("a")!!.createdAtMs
        assertTrue(created > 0)
        r.upsert(r.get("a")!!.copy(title = "更新", status = Status.PLANNED))
        assertEquals(1, r.ideas.value.size)
        assertEquals("更新", r.get("a")!!.title)
        assertEquals(created, r.get("a")!!.createdAtMs)
        assertEquals(Status.PLANNED, r.get("a")!!.status)
    }

    @Test
    fun `まとめて追加・削除できる`() = runTest {
        val r = repo(tmp.newFolder(), StandardTestDispatcher(testScheduler))
        r.load()
        r.addAll(listOf(idea("a"), idea("b"), idea("c")))
        r.delete("b")
        assertEquals(listOf("a", "c"), r.ideas.value.map { it.id })
    }

    @Test
    fun `壊れたファイルは、消さずに退避して空から始める`() = runTest {
        val dir = tmp.newFolder()
        File(dir, "ideas.json").writeText("{壊れた")
        val r = repo(dir, StandardTestDispatcher(testScheduler))
        r.load()
        assertTrue(r.loaded.value)
        assertTrue(r.ideas.value.isEmpty())
        val saved = dir.listFiles()!!.map { it.name }
        assertTrue(saved.toString(), saved.any { it.startsWith("ideas.json.corrupt-") })
        r.upsert(idea("a"))
        assertEquals(listOf("a"), r.ideas.value.map { it.id })
    }

    @Test
    fun `保存の途中の一時ファイルが残らない`() = runTest {
        val dir = tmp.newFolder()
        val r = repo(dir, StandardTestDispatcher(testScheduler))
        r.load()
        r.upsert(idea("a"))
        assertEquals(listOf("ideas.json"), dir.listFiles()!!.map { it.name })
    }
}
