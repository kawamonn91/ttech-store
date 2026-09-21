package com.ttech.colorpalette.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ttech.colorpalette.data.SavedPalette
import com.ttech.colorpalette.data.SavedPaletteStore
import com.ttech.colorpalette.domain.ColorPalette
import com.ttech.colorpalette.domain.Harmony
import com.ttech.colorpalette.domain.isValidHex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ColorPaletteScreen() {
    val context = LocalContext.current
    val store = remember { SavedPaletteStore(context) }
    val scope = rememberCoroutineScope()
    val saved by store.saved.collectAsState(initial = emptyList())

    var hexInput by remember { mutableStateOf("#2563eb") }
    var harmony by remember { mutableStateOf(Harmony.COMPLEMENTARY) }
    var copiedHex by remember { mutableStateOf<String?>(null) }

    val colors = if (isValidHex(hexInput)) ColorPalette.generate(hexInput, harmony) else null

    LaunchedEffect(copiedHex) {
        if (copiedHex != null) {
            delay(1200)
            copiedHex = null
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("配色パレット") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("ベースカラー", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(runCatching { Color(android.graphics.Color.parseColor(hexInput)) }.getOrDefault(Color.Gray)),
                            )
                            OutlinedTextField(
                                value = hexInput,
                                onValueChange = { hexInput = it.take(7) },
                                isError = !isValidHex(hexInput),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("配色ルール", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val rows = Harmony.entries.chunked(2)
                        rows.forEach { rowItems ->
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { h ->
                                    HarmonyChip(
                                        label = h.label,
                                        selected = harmony == h,
                                        onClick = { harmony = h },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        if (colors != null) {
                            PaletteRow(
                                colors = colors,
                                height = 72.dp,
                                copiedHex = copiedHex,
                                onTap = { hex ->
                                    copyToClipboard(context, hex)
                                    copiedHex = hex
                                },
                            )
                            Button(
                                onClick = { scope.launch { store.add(colors) } },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            ) { Text("この配色を保存") }
                        } else {
                            Text(
                                "#RRGGBB形式で正しいカラーコードを入力してください。",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            if (saved.isNotEmpty()) {
                item {
                    Text(
                        "保存した配色",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(saved, key = SavedPalette::id) { palette ->
                    PaletteRow(colors = palette.colors, height = 32.dp)
                }
            }
        }
    }
}

@Composable
private fun HarmonyChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .then(Modifier.border(BorderStroke(1.dp, border), RoundedCornerShape(999.dp)))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = textColor, style = MaterialTheme.typography.labelLarge)
    }
}

/** copiedHex/onTapを渡すとタップでコピーできる一覧行、渡さなければ表示専用(保存済み配色用)。 */
@Composable
private fun PaletteRow(
    colors: List<String>,
    height: androidx.compose.ui.unit.Dp,
    copiedHex: String? = null,
    onTap: ((String) -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))) {
        colors.forEach { hex ->
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .background(runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray))
                    .then(if (onTap != null) Modifier.clickable { onTap(hex) } else Modifier),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (height > 40.dp) {
                    Text(
                        if (copiedHex == hex) "コピーしました" else hex,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("hex", text))
}
