package com.ttech.weightlog.data

import com.ttech.weightlog.domain.Limits
import com.ttech.weightlog.domain.Profile
import com.ttech.weightlog.domain.WeightEntry
import com.ttech.weightlog.domain.WeightJson
import com.ttech.weightlog.domain.sanitized
import com.ttech.weightlog.domain.upsertByDate
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
private data class Library(val version: Int = 1, val entries: List<WeightEntry> = emptyList(), val profile: Profile = Profile())

/**
 * 記録の保管庫。すべてを1つのJSONファイルに保存する(端末の中だけ。通信はしない)。
 * 書き込みは、いったん別のファイルに書いてから置き換えるので、途中で終了しても壊れない。
 */
class WeightRepository(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<WeightEntry>>(emptyList())
    private val _profile = MutableStateFlow(Profile())
    private val _loaded = MutableStateFlow(false)

    val entries: StateFlow<List<WeightEntry>> = _entries.asStateFlow()
    val profile: StateFlow<Profile> = _profile.asStateFlow()
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun load() = withContext(io) {
        mutex.withLock {
            val lib = read()
            _entries.value = lib.entries
            _profile.value = lib.profile
            _loaded.value = true
        }
    }

    private fun read(): Library {
        if (!file.exists()) return Library()
        return try {
            val lib = WeightJson.decodeFromString<Library>(file.readText())
            lib.copy(entries = lib.entries.take(Limits.MAX_ENTRIES).map { it.sanitized() }, profile = lib.profile.sanitized())
        } catch (_: Exception) {
            // 読めないファイルは消さずに退避して、空の状態から始める(手作業での復旧の余地を残す)
            runCatching { file.renameTo(File(file.parentFile, "${file.name}.corrupt-${clock()}")) }
            Library()
        }
    }

    private fun write(entries: List<WeightEntry>, profile: Profile) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(WeightJson.encodeToString(Library(entries = entries, profile = profile)))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private suspend fun updateEntries(transform: (List<WeightEntry>) -> List<WeightEntry>) = withContext(io) {
        mutex.withLock {
            val next = transform(_entries.value).take(Limits.MAX_ENTRIES)
            write(next, _profile.value)
            _entries.value = next
        }
    }

    /** 同じ日付の記録があれば置き換え、無ければ追加する */
    suspend fun upsert(entry: WeightEntry) = updateEntries { list ->
        val now = clock()
        val existing = list.firstOrNull { it.date == entry.date }
        val clean = entry.sanitized(now).copy(
            id = existing?.id ?: entry.id,
            createdAtMs = existing?.createdAtMs?.takeIf { it > 0 } ?: now,
        )
        upsertByDate(list, clean)
    }

    suspend fun addAll(newEntries: List<WeightEntry>) = updateEntries { list ->
        newEntries.fold(list) { acc, e -> upsertByDate(acc, e) }
    }

    suspend fun delete(id: String) = updateEntries { list -> list.filterNot { it.id == id } }

    suspend fun updateProfile(transform: (Profile) -> Profile) = withContext(io) {
        mutex.withLock {
            val next = transform(_profile.value).sanitized()
            write(_entries.value, next)
            _profile.value = next
        }
    }

    fun get(id: String): WeightEntry? = _entries.value.firstOrNull { it.id == id }
    fun getByDate(date: String): WeightEntry? = _entries.value.firstOrNull { it.date == date }
}
