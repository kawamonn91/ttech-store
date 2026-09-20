package com.kawamonn.store.update

import com.kawamonn.store.data.api.IndexEntryDto
import com.kawamonn.store.data.api.LatestReleaseDto
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateDetectorTest {
    private fun entry(pkg: String, code: Long) = IndexEntryDto(
        slug = pkg.substringAfterLast('.'),
        packageName = pkg,
        name = pkg,
        latest = LatestReleaseDto(releaseId = "r-$pkg-$code", versionName = "v$code", versionCode = code),
    )

    private val index = listOf(entry("com.a", 3), entry("com.b", 5), entry("com.c", 2))

    @Test
    fun `インストール済みで新しい版があるものだけが対象`() {
        val installed = mapOf("com.a" to 2L, "com.b" to 5L)
        val result = UpdateDetector.detect(index) { installed[it] }
        assertEquals(listOf("com.a"), result.map { it.entry.packageName })
        assertEquals(2L, result.single().installedVersionCode)
    }

    @Test
    fun `未インストールは対象外`() {
        assertEquals(emptyList<AvailableUpdate>(), UpdateDetector.detect(index) { null })
    }

    @Test
    fun `端末の方が新しい場合は対象外`() {
        assertEquals(emptyList<AvailableUpdate>(), UpdateDetector.detect(index) { 99L })
    }
}
