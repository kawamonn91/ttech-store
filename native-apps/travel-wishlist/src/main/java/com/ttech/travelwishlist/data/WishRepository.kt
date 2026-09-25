package com.ttech.travelwishlist.data

import com.ttech.travelwishlist.domain.Limits
import com.ttech.travelwishlist.domain.Place
import com.ttech.travelwishlist.domain.WishJson
import com.ttech.travelwishlist.domain.sanitized
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
private data class Library(val version: Int = 1, val places: List<Place> = emptyList())

/**
 * 行きたい場所の保管庫。すべてを1つのJSONファイルに保存する(端末の中だけ。通信はしない)。
 * 書き込みは、いったん別のファイルに書いてから置き換えるので、途中で終了しても壊れない。
 */
class WishRepository(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _places = MutableStateFlow<List<Place>>(emptyList())
    private val _loaded = MutableStateFlow(false)

    val places: StateFlow<List<Place>> = _places.asStateFlow()
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun load() = withContext(io) {
        mutex.withLock {
            _places.value = read()
            _loaded.value = true
        }
    }

    private fun read(): List<Place> {
        if (!file.exists()) return emptyList()
        return try {
            WishJson.decodeFromString<Library>(file.readText()).places.take(Limits.MAX_PLACES).map { it.sanitized() }
        } catch (_: Exception) {
            // 読めないファイルは消さずに退避して、空の状態から始める(手作業での復旧の余地を残す)
            runCatching { file.renameTo(File(file.parentFile, "${file.name}.corrupt-${clock()}")) }
            emptyList()
        }
    }

    private fun write(places: List<Place>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(WishJson.encodeToString(Library(places = places)))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    suspend fun update(transform: (List<Place>) -> List<Place>) = withContext(io) {
        mutex.withLock {
            val next = transform(_places.value).take(Limits.MAX_PLACES)
            write(next)
            _places.value = next
        }
    }

    /** 追加または更新(同じ id なら置き換え)。更新日時を新しくし、内容を整える */
    suspend fun upsert(place: Place) = update { list ->
        val now = clock()
        val clean = place.sanitized(now).let { if (it.createdAtMs == 0L) it.copy(createdAtMs = now) else it }
        if (list.any { it.id == clean.id }) list.map { if (it.id == clean.id) clean else it } else list + clean
    }

    suspend fun addAll(places: List<Place>) = update { it + places }

    suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    fun get(id: String): Place? = _places.value.firstOrNull { it.id == id }
}
