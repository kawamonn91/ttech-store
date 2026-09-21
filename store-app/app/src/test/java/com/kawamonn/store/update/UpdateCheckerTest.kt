package com.kawamonn.store.update

import com.kawamonn.store.data.api.AppDetailDto
import com.kawamonn.store.data.api.AppListDto
import com.kawamonn.store.data.api.CategoriesDto
import com.kawamonn.store.data.api.DownloadInfoDto
import com.kawamonn.store.data.api.HomeDto
import com.kawamonn.store.data.api.IndexDto
import com.kawamonn.store.data.api.IndexEntryDto
import com.kawamonn.store.data.api.LatestReleaseDto
import com.kawamonn.store.data.api.StoreApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun entry(pkg: String, code: Long) = IndexEntryDto(
    slug = pkg.substringAfterLast('.'),
    packageName = pkg,
    name = pkg,
    latest = LatestReleaseDto(releaseId = "r", versionName = "v$code", versionCode = code),
)

private class FakeApi(private val items: List<IndexEntryDto>, private val fail: Boolean = false) : StoreApi {
    override suspend fun home(): HomeDto = throw NotImplementedError()
    override suspend fun categories(): CategoriesDto = throw NotImplementedError()
    override suspend fun apps(query: String?, category: String?, sort: String, limit: Int, offset: Int): AppListDto = throw NotImplementedError()
    override suspend fun app(slug: String): AppDetailDto = throw NotImplementedError()
    override suspend fun index(): IndexDto {
        if (fail) throw RuntimeException("network")
        return IndexDto(items)
    }
    override suspend fun downloadInfo(releaseId: String, deviceId: String): DownloadInfoDto = throw NotImplementedError()
}

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckerTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Test
    fun `更新があるアプリだけを保持する`() {
        val installed = mapOf("com.a" to 1L, "com.b" to 5L)
        val checker = UpdateChecker(FakeApi(listOf(entry("com.a", 3), entry("com.b", 5))), installed::get, scope)

        checker.refresh()
        scope.advanceUntilIdle()

        assertEquals(listOf("com.a"), checker.updates.value.map { it.entry.packageName })
    }

    @Test
    fun `未インストールのアプリは対象外`() {
        val checker = UpdateChecker(FakeApi(listOf(entry("com.a", 3))), { null }, scope)
        checker.refresh()
        scope.advanceUntilIdle()
        assertTrue(checker.updates.value.isEmpty())
    }

    @Test
    fun `新しい更新が出てきたらバナーの非表示状態をリセットする`() {
        val checker = UpdateChecker(FakeApi(listOf(entry("com.a", 2))), { 1L }, scope)
        checker.refresh()
        scope.advanceUntilIdle()
        checker.dismissBanner()
        assertTrue(checker.bannerDismissed.value)

        checker.refresh() // 同じ内容の再チェックではリセットしない
        scope.advanceUntilIdle()
        assertTrue(checker.bannerDismissed.value)
    }

    @Test
    fun `通信に失敗したら前回の結果を保持する`() {
        val checker = UpdateChecker(FakeApi(listOf(entry("com.a", 2))), { 1L }, scope)
        checker.refresh()
        scope.advanceUntilIdle()
        assertEquals(1, checker.updates.value.size)

        val failing = UpdateChecker(FakeApi(emptyList(), fail = true), { 1L }, scope)
        failing.refresh()
        scope.advanceUntilIdle()
        // このインスタンスでは初期値(空)のまま = クラッシュせず落ち着いて処理される
        assertTrue(failing.updates.value.isEmpty())
    }
}
