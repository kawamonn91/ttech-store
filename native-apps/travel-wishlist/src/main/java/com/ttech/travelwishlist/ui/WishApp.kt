package com.ttech.travelwishlist.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.ttech.travelwishlist.data.WishRepository
import com.ttech.travelwishlist.domain.Place
import com.ttech.travelwishlist.domain.Status
import com.ttech.travelwishlist.domain.WishFile
import com.ttech.travelwishlist.domain.markVisited
import com.ttech.travelwishlist.domain.reopen
import com.ttech.travelwishlist.domain.samplePlaces
import com.ttech.travelwishlist.share.shareBackup
import com.ttech.travelwishlist.share.shareWishlist
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Tab(val label: String, val title: String) {
    LIST("リスト", "行きたい旅メモ"),
    DISCOVER("みつける", "みつける"),
    RECORD("記録", "旅の記録"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishApp(repository: WishRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loaded by repository.loaded.collectAsState()
    val places by repository.places.collectAsState()
    val today = remember { LocalDate.now() }

    var tab by rememberSaveable { mutableStateOf(Tab.LIST) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Place?>(null) }
    var adding by remember { mutableStateOf(false) }
    var visitFor by remember { mutableStateOf<Place?>(null) }
    var menu by remember { mutableStateOf(false) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    fun newId() = UUID.randomUUID().toString()

    // 「メモファイル」(バックアップ)を読み込む(3MBまで。読み込んだものは、追加する)
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { readLimited(it, WishFile.MAX_BYTES + 1) }
                }.getOrNull()
            }
            if (text == null) {
                toast("ファイルを読み込めませんでした")
                return@launch
            }
            when (val decoded = WishFile.decode(text, ::newId, System.currentTimeMillis())) {
                is WishFile.Decoded.Ok -> {
                    repository.addAll(decoded.places)
                    toast("${decoded.places.size}件を読み込みました")
                }
                is WishFile.Decoded.Error -> toast(decoded.message)
            }
        }
    }

    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val open = places.firstOrNull { it.id == openId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tab.title) },
                actions = {
                    if (tab == Tab.LIST) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "その他") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("リストをテキストで共有") },
                                    enabled = places.isNotEmpty(),
                                    onClick = { menu = false; runCatching { shareWishlist(context, places) } },
                                )
                                DropdownMenuItem(
                                    text = { Text("バックアップを共有(ファイル)") },
                                    enabled = places.isNotEmpty(),
                                    onClick = { menu = false; runCatching { shareBackup(context, places) } },
                                )
                                DropdownMenuItem(text = { Text("メモファイルを読み込む") }, onClick = { menu = false; importer.launch(arrayOf("*/*")) })
                                DropdownMenuItem(
                                    text = { Text("サンプルを追加") },
                                    onClick = { menu = false; scope.launch { repository.addAll(samplePlaces(today, ::newId, System.currentTimeMillis())) } },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.LIST -> Icons.Filled.FavoriteBorder
                                    Tab.DISCOVER -> Icons.Filled.Explore
                                    Tab.RECORD -> Icons.Filled.Place
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.LIST && places.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("行きたい場所を追加") },
                )
            }
        },
    ) { padding ->
        when (tab) {
            Tab.LIST -> ListScreen(
                places = places,
                currentMonth = today.monthValue,
                contentPadding = padding,
                onOpen = { openId = it.id },
                onAdd = { adding = true },
                onAddSample = { scope.launch { repository.addAll(samplePlaces(today, ::newId, System.currentTimeMillis())) } },
                onImport = { importer.launch(arrayOf("*/*")) },
            )
            Tab.DISCOVER -> DiscoverScreen(
                places = places,
                currentMonth = today.monthValue,
                contentPadding = padding,
                onOpen = { openId = it.id },
                onSetStatus = { place, status -> scope.launch { repository.upsert(place.reopen(status, System.currentTimeMillis())) } },
                onAdd = { adding = true },
            )
            Tab.RECORD -> RecordScreen(places, padding, onOpen = { openId = it.id })
        }
    }

    if (adding) {
        PlaceEditDialog(
            place = null,
            onSave = { place -> scope.launch { repository.upsert(place) }; adding = false; tab = Tab.LIST },
            onDismiss = { adding = false },
        )
    }
    editing?.let { current ->
        PlaceEditDialog(
            place = current,
            onSave = { place -> scope.launch { repository.upsert(place) }; editing = null },
            onDismiss = { editing = null },
        )
    }
    visitFor?.let { current ->
        VisitDialog(
            place = current,
            today = today,
            onSave = { date, impression, rating ->
                scope.launch { repository.upsert(current.markVisited(date, impression, rating, System.currentTimeMillis())) }
                visitFor = null
            },
            onDismiss = { visitFor = null },
        )
    }
    if (open != null) {
        PlaceDetailSheet(
            place = open,
            onDismiss = { openId = null },
            onEdit = { editing = open },
            onVisited = { visitFor = open },
            onSetStatus = { status: Status -> scope.launch { repository.upsert(open.reopen(status, System.currentTimeMillis())) } },
            onDelete = { scope.launch { repository.delete(open.id); openId = null } },
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
