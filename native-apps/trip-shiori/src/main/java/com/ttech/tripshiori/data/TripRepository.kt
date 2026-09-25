package com.ttech.tripshiori.data

import com.ttech.tripshiori.domain.Limits
import com.ttech.tripshiori.domain.Trip
import com.ttech.tripshiori.domain.TripJson
import com.ttech.tripshiori.domain.sanitized
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
private data class Library(val version: Int = 1, val trips: List<Trip> = emptyList())

/**
 * しおりの保管庫。すべてのしおりを1つのJSONファイルに保存する(端末の中だけ。通信はしない)。
 * 書き込みは、いったん別のファイルに書いてから置き換えるので、途中で終了しても壊れない。
 */
class TripRepository(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    private val _loaded = MutableStateFlow(false)

    /** 更新の新しい順 */
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    /** 保存されたデータを読み終えたか。読み終える前に画面を出すと、空に見えてしまうため */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun load() = withContext(io) {
        mutex.withLock {
            _trips.value = read().sortedByDescending { it.updatedAtMs }
            _loaded.value = true
        }
    }

    private fun read(): List<Trip> {
        if (!file.exists()) return emptyList()
        return try {
            TripJson.decodeFromString<Library>(file.readText()).trips.take(Limits.MAX_TRIPS).map { it.sanitized() }
        } catch (_: Exception) {
            // 読めないファイルは消さずに退避して、空の状態から始める(手作業での復旧の余地を残す)
            runCatching { file.renameTo(File(file.parentFile, "${file.name}.corrupt-${clock()}")) }
            emptyList()
        }
    }

    private fun write(trips: List<Trip>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(TripJson.encodeToString(Library(trips = trips)))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    /** 全体を更新して保存する。更新後は [Trip.updatedAtMs] の新しい順に並べ直す */
    suspend fun update(transform: (List<Trip>) -> List<Trip>) = withContext(io) {
        mutex.withLock {
            val next = transform(_trips.value).take(Limits.MAX_TRIPS).sortedByDescending { it.updatedAtMs }
            write(next)
            _trips.value = next
        }
    }

    /** 1つのしおりを更新する(更新日時も新しくする)。存在しなければ何もしない */
    suspend fun updateTrip(id: String, transform: (Trip) -> Trip) = update { list ->
        list.map { if (it.id == id) transform(it).sanitized(clock()) else it }
    }

    suspend fun add(trip: Trip) = update { it + trip.sanitized(clock()) }

    suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    fun get(id: String): Trip? = _trips.value.firstOrNull { it.id == id }
}
