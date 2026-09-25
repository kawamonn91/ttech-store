package com.ttech.tripshiori.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ttech.tripshiori.data.TripRepository
import com.ttech.tripshiori.domain.Trip
import com.ttech.tripshiori.domain.TripFile
import com.ttech.tripshiori.domain.sampleTrip
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ShioriApp(repository: TripRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loaded by repository.loaded.collectAsState()
    val trips by repository.trips.collectAsState()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val today = remember { LocalDate.now() }

    fun newId() = UUID.randomUUID().toString()
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    // 共有された「しおりファイル」を読み込む(2MBまで。読み込んだものは、新しいしおりとして追加する)
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { readLimited(it, TripFile.MAX_BYTES + 1) }
                }.getOrNull()
            }
            if (text == null) {
                toast("ファイルを読み込めませんでした")
                return@launch
            }
            when (val decoded = TripFile.decode(text, ::newId, System.currentTimeMillis())) {
                is TripFile.Decoded.Ok -> {
                    repository.add(decoded.trip)
                    openId = decoded.trip.id
                    toast("「${decoded.trip.title}」を読み込みました")
                }
                is TripFile.Decoded.Error -> toast(decoded.message)
            }
        }
    }

    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val open = trips.firstOrNull { it.id == openId }
    if (open != null) {
        BackHandler { openId = null }
        TripDetailScreen(
            trip = open,
            today = today,
            onBack = { openId = null },
            onUpdate = { transform -> scope.launch { repository.updateTrip(open.id, transform) } },
            onDuplicate = {
                scope.launch {
                    val copy = open.duplicated(::newId)
                    repository.add(copy)
                    openId = copy.id
                    toast("複製しました")
                }
            },
            onDelete = { scope.launch { repository.delete(open.id); openId = null } },
        )
    } else {
        TripListScreen(
            trips = trips,
            today = today,
            onOpen = { openId = it.id },
            onCreate = { trip -> scope.launch { repository.add(trip); openId = trip.id } },
            onAddSample = {
                scope.launch {
                    val sample = sampleTrip(today, ::newId, System.currentTimeMillis())
                    repository.add(sample)
                    openId = sample.id
                }
            },
            onImport = { importer.launch(arrayOf("*/*")) },
        )
    }
}

/** 上限までだけ読む(巨大なファイルを選ばれても、メモリを使い切らないように) */
private fun readLimited(input: java.io.InputStream, max: Int): String {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < max) {
        val n = input.read(buffer, 0, minOf(buffer.size, max - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toString(Charsets.UTF_8.name())
}

/** 複製。idを新しくし、持ち物のチェックは外す */
private fun Trip.duplicated(newId: () -> String): Trip = copy(
    id = newId(),
    title = "$title のコピー".take(100),
    items = items.map { it.copy(id = newId()) },
    packing = packing.map { it.copy(id = newId(), checked = false) },
    lodgings = lodgings.map { it.copy(id = newId()) },
    contacts = contacts.map { it.copy(id = newId()) },
)
