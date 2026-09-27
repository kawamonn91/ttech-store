package com.ttech.ideamemo.data

import com.ttech.ideamemo.domain.Idea
import com.ttech.ideamemo.domain.IdeaJson
import com.ttech.ideamemo.domain.Limits
import com.ttech.ideamemo.domain.sanitized
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
private data class Library(val version: Int = 1, val ideas: List<Idea> = emptyList())

/**
 * アイデアの保管庫。すべてを1つのJSONファイルに保存する(端末の中だけ。通信はしない)。
 * 書き込みは、いったん別のファイルに書いてから置き換えるので、途中で終了しても壊れない。
 */
class IdeaRepository(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _ideas = MutableStateFlow<List<Idea>>(emptyList())
    private val _loaded = MutableStateFlow(false)

    val ideas: StateFlow<List<Idea>> = _ideas.asStateFlow()
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun load() = withContext(io) {
        mutex.withLock {
            _ideas.value = read()
            _loaded.value = true
        }
    }

    private fun read(): List<Idea> {
        if (!file.exists()) return emptyList()
        return try {
            IdeaJson.decodeFromString<Library>(file.readText()).ideas.take(Limits.MAX_IDEAS).map { it.sanitized() }
        } catch (_: Exception) {
            // 読めないファイルは消さずに退避して、空の状態から始める(手作業での復旧の余地を残す)
            runCatching { file.renameTo(File(file.parentFile, "${file.name}.corrupt-${clock()}")) }
            emptyList()
        }
    }

    private fun write(ideas: List<Idea>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(IdeaJson.encodeToString(Library(ideas = ideas)))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    suspend fun update(transform: (List<Idea>) -> List<Idea>) = withContext(io) {
        mutex.withLock {
            val next = transform(_ideas.value).take(Limits.MAX_IDEAS)
            write(next)
            _ideas.value = next
        }
    }

    /** 追加または更新(同じ id なら置き換え)。更新日時を新しくし、内容を整える */
    suspend fun upsert(idea: Idea) = update { list ->
        val now = clock()
        val clean = idea.sanitized(now).let { if (it.createdAtMs == 0L) it.copy(createdAtMs = now) else it }
        if (list.any { it.id == clean.id }) list.map { if (it.id == clean.id) clean else it } else list + clean
    }

    suspend fun addAll(ideas: List<Idea>) = update { it + ideas }

    suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    fun get(id: String): Idea? = _ideas.value.firstOrNull { it.id == id }
}
