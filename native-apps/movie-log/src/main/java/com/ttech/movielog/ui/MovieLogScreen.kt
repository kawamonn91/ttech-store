package com.ttech.movielog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ttech.movielog.data.MovieStore
import com.ttech.movielog.domain.MOVIE_KINDS
import com.ttech.movielog.domain.Movie
import com.ttech.movielog.domain.buildMovie
import com.ttech.movielog.domain.heading
import com.ttech.movielog.domain.memoSuffix
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

private val StarColor = Color(0xFFFBBF24)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MovieLogScreen() {
    val context = LocalContext.current
    val store = remember { MovieStore(context) }
    val scope = rememberCoroutineScope()
    val movies by store.movies.collectAsState(initial = emptyList())

    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(MOVIE_KINDS.first()) }
    var rating by remember { mutableIntStateOf(4) }
    var memo by remember { mutableStateOf("") }

    fun addMovie() {
        val movie = buildMovie(UUID.randomUUID().toString(), title, kind, rating, LocalDate.now().toString(), memo) ?: return
        scope.launch { store.add(movie) }
        title = ""; memo = ""
    }

    Scaffold(topBar = { TopAppBar(title = { Text("映画・ドラマ視聴記録") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("記録した作品数", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${movies.size}本", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("作品名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            MOVIE_KINDS.forEachIndexed { index, k ->
                                SegmentedButton(
                                    selected = kind == k,
                                    onClick = { kind = k },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = MOVIE_KINDS.size),
                                ) { Text(k) }
                            }
                        }
                        Text("評価", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            (1..5).forEach { r ->
                                SegmentedButton(
                                    selected = rating == r,
                                    onClick = { rating = r },
                                    shape = SegmentedButtonDefaults.itemShape(index = r - 1, count = 5),
                                ) { Text("$r") }
                            }
                        }
                        OutlinedTextField(
                            value = memo,
                            onValueChange = { memo = it },
                            label = { Text("感想(任意)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = ::addMovie, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text("記録する")
                        }
                    }
                }
            }

            item {
                Text("視聴記録", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (movies.isEmpty()) {
                item { Text("まだ記録がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(movies, key = Movie::id) { m ->
                val emptyStar = MaterialTheme.colorScheme.outlineVariant
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.heading(), style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildAnnotatedString {
                                    append("${m.watchedDate} ・ ")
                                    withStyle(SpanStyle(color = StarColor)) { append("★".repeat(m.rating)) }
                                    withStyle(SpanStyle(color = emptyStar)) { append("★".repeat(5 - m.rating)) }
                                    append(m.memoSuffix())
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { scope.launch { store.remove(m.id) } }) { Text("削除") }
                    }
                }
            }
        }
    }
}
