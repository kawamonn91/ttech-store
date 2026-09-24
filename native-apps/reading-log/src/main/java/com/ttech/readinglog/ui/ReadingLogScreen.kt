package com.ttech.readinglog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ttech.readinglog.data.BookStore
import com.ttech.readinglog.domain.Book
import com.ttech.readinglog.domain.detailPrefix
import com.ttech.readinglog.domain.statsForYear
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReadingLogScreen() {
    val context = LocalContext.current
    val store = remember { BookStore(context) }
    val scope = rememberCoroutineScope()
    val books by store.books.collectAsState(initial = emptyList())

    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf("") }
    var rating by remember { mutableIntStateOf(4) }

    fun addBook() {
        if (title.isBlank()) return
        val book = Book(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            author = author.trim(),
            pages = pages.toIntOrNull() ?: 0,
            rating = rating,
            finishedDate = LocalDate.now().toString(),
        )
        scope.launch { store.add(book) }
        title = ""; author = ""; pages = ""
    }

    val stats = books.statsForYear(LocalDate.now().year.toString())
    val numberFormat = remember { NumberFormat.getIntegerInstance() }

    Scaffold(topBar = { TopAppBar(title = { Text("読書メーター") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat("今年読んだ冊数", "${stats.count}冊")
                        Stat("今年の総ページ数", "${numberFormat.format(stats.totalPages)}p")
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("タイトル") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = author,
                                onValueChange = { author = it },
                                label = { Text("著者(任意)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = pages,
                                onValueChange = { v -> pages = v.filter { it.isDigit() }.take(5) },
                                label = { Text("ページ数(任意)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text("評価", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        RatingSelector(rating) { rating = it }
                        Button(onClick = ::addBook, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text("読了として記録")
                        }
                    }
                }
            }

            item {
                Text(
                    "読了一覧(${books.size}件)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (books.isEmpty()) {
                item { Text("まだ記録がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(books, key = Book::id) { b ->
                val emptyStar = MaterialTheme.colorScheme.outlineVariant
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildAnnotatedString {
                                    append("${b.detailPrefix()}${b.finishedDate} ・ ")
                                    appendStars(b.rating, emptyStar)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { scope.launch { store.remove(b.id) } }) { Text("削除") }
                    }
                }
            }
        }
    }
}

private val StarColor = Color(0xFFFBBF24)

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendStars(value: Int, emptyColor: Color) {
    withStyle(SpanStyle(color = StarColor)) { append("★".repeat(value)) }
    withStyle(SpanStyle(color = emptyColor)) { append("★".repeat(5 - value)) }
}

@Composable
private fun RatingSelector(value: Int, onChange: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        (1..5).forEach { r ->
            SegmentedButton(
                selected = value == r,
                onClick = { onChange(r) },
                shape = SegmentedButtonDefaults.itemShape(index = r - 1, count = 5),
            ) { Text("$r") }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall)
    }
}
