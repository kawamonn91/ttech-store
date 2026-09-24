package com.ttech.tutorprogress.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttech.tutorprogress.data.StudentStore
import com.ttech.tutorprogress.domain.ProgressNote
import com.ttech.tutorprogress.domain.Student
import com.ttech.tutorprogress.domain.addNote
import com.ttech.tutorprogress.domain.addStudent
import com.ttech.tutorprogress.domain.removeStudent
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TutorProgressScreen() {
    val context = LocalContext.current
    val store = remember { StudentStore(context) }
    val scope = rememberCoroutineScope()
    val students by store.students.collectAsState(initial = emptyList())

    var newStudentName by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var noteText by remember { mutableStateOf("") }

    fun addStudent() {
        if (newStudentName.isBlank()) return
        val student = Student(id = UUID.randomUUID().toString(), name = newStudentName.trim())
        scope.launch { store.update { it.addStudent(student) } }
        newStudentName = ""
        selectedId = student.id
    }

    fun removeStudent(id: String) {
        scope.launch { store.update { it.removeStudent(id) } }
        if (selectedId == id) selectedId = null
    }

    fun addNote(studentId: String) {
        val note = ProgressNote(UUID.randomUUID().toString(), LocalDate.now().toString(), noteText)
        scope.launch { store.update { it.addNote(studentId, note) } }
        noteText = ""
    }

    val selected = students.firstOrNull { it.id == selectedId }

    Scaffold(topBar = { TopAppBar(title = { Text("生徒別進捗管理") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newStudentName,
                            onValueChange = { newStudentName = it },
                            label = { Text("生徒名を追加") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = ::addStudent, enabled = newStudentName.isNotBlank()) { Text("追加") }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            "生徒一覧",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        if (students.isEmpty()) {
                            Text("まだ生徒がいません", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                        } else {
                            Row(
                                Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                students.forEach { s ->
                                    FilterChip(selected = s.id == selectedId, onClick = { selectedId = s.id }, label = { Text(s.name) })
                                }
                            }
                        }
                    }
                }
            }

            if (selected != null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${selected.name}さんの進捗", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { removeStudent(selected.id) }) { Text("生徒を削除") }
                            }
                            OutlinedTextField(
                                value = noteText,
                                onValueChange = { noteText = it },
                                placeholder = { Text("今日の内容・理解度・宿題など") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(onClick = { addNote(selected.id) }, enabled = noteText.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                                Text("記録を追加")
                            }
                        }
                    }
                }

                item {
                    Text("記録履歴", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (selected.notes.isEmpty()) {
                    item { Text("まだ記録がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }

                items(selected.notes, key = ProgressNote::id) { n ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(n.date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(n.content, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
