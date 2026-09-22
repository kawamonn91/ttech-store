package com.ttech.businesscardmaker.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttech.businesscardmaker.domain.CARD_THEMES
import com.ttech.businesscardmaker.domain.CardTheme
import com.ttech.businesscardmaker.domain.cardLine
import com.ttech.common.share.shareBitmap

private const val CARD_WIDTH = 600f
private const val CARD_HEIGHT = 340f

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BusinessCardMakerScreen() {
    val context = LocalContext.current
    var name by remember { mutableStateOf("山田 太郎") }
    var title by remember { mutableStateOf("代表") }
    var company by remember { mutableStateOf("株式会社サンプル") }
    var contact by remember { mutableStateOf("090-1234-5678 / mail@example.com") }
    var themeIndex by remember { mutableStateOf(0) }
    val theme = CARD_THEMES[themeIndex]

    val displayName = cardLine(name, "お名前")
    val displayCompany = cardLine(company, "会社名・屋号")
    val displayContact = cardLine(contact, "連絡先")

    Scaffold(topBar = { TopAppBar(title = { Text("名刺メーカー") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("氏名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("肩書き") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = company,
                                onValueChange = { company = it },
                                label = { Text("会社・屋号") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedTextField(
                            value = contact,
                            onValueChange = { contact = it },
                            label = { Text("連絡先") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("デザイン", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CARD_THEMES.forEachIndexed { i, t ->
                                ThemeChip(
                                    label = t.label,
                                    selected = themeIndex == i,
                                    onClick = { themeIndex = i },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(CARD_WIDTH / CARD_HEIGHT)
                                .clip(RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(8.dp)),
                        ) {
                            drawIntoCanvas { canvas ->
                                drawBusinessCard(
                                    canvas = canvas.nativeCanvas,
                                    width = size.width,
                                    height = size.height,
                                    name = displayName,
                                    title = title,
                                    company = displayCompany,
                                    contact = displayContact,
                                    theme = theme,
                                )
                            }
                        }
                        Button(
                            onClick = {
                                val bitmap = renderCardBitmap(displayName, title, displayCompany, displayContact, theme)
                                shareBitmap(context, bitmap, "business-card.png", "名刺画像を共有")
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) { Text("画像として共有・保存") }
                    }
                }
            }
        }
    }
}

/** 共有用に高解像度(3倍)でBitmapへ描画する。プレビューと同じ [drawBusinessCard] を使うので見た目は一致する。 */
private fun renderCardBitmap(name: String, title: String, company: String, contact: String, theme: CardTheme): Bitmap {
    val scale = 3
    val bitmap = Bitmap.createBitmap((CARD_WIDTH * scale).toInt(), (CARD_HEIGHT * scale).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    drawBusinessCard(canvas, bitmap.width.toFloat(), bitmap.height.toFloat(), name, title, company, contact, theme)
    return bitmap
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.foundation.layout.Box(
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
