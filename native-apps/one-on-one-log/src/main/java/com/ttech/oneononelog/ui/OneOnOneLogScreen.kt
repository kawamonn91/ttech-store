package com.ttech.oneononelog.ui

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
import com.ttech.oneononelog.data.MemberStore
import com.ttech.oneononelog.domain.Member
import com.ttech.oneononelog.domain.OneOnOneRecord
import com.ttech.oneononelog.domain.addMember
import com.ttech.oneononelog.domain.addRecord
import com.ttech.oneononelog.domain.buildRecord
import com.ttech.oneononelog.domain.displayLines
import com.ttech.oneononelog.domain.removeMember
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OneOnOneLogScreen() {
    val context = LocalContext.current
    val store = remember { MemberStore(context) }
    val scope = rememberCoroutineScope()
    val members by store.members.collectAsState(initial = emptyList())

    var newMemberName by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var goodThings by remember { mutableStateOf("") }
    var concerns by remember { mutableStateOf("") }
    var nextActions by remember { mutableStateOf("") }

    fun addMember() {
        if (newMemberName.isBlank()) return
        val member = Member(id = UUID.randomUUID().toString(), name = newMemberName.trim())
        scope.launch { store.update { it.addMember(member) } }
        newMemberName = ""
        selectedId = member.id
    }

    fun removeMember(id: String) {
        scope.launch { store.update { it.removeMember(id) } }
        if (selectedId == id) selectedId = null
    }

    fun addRecord(memberId: String) {
        val record = buildRecord(UUID.randomUUID().toString(), LocalDate.now().toString(), goodThings, concerns, nextActions)
        scope.launch { store.update { it.addRecord(memberId, record) } }
        goodThings = ""; concerns = ""; nextActions = ""
    }

    val selected = members.firstOrNull { it.id == selectedId }

    Scaffold(topBar = { TopAppBar(title = { Text("1on1記録") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newMemberName,
                            onValueChange = { newMemberName = it },
                            label = { Text("メンバー名を追加") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = ::addMember, enabled = newMemberName.isNotBlank()) { Text("追加") }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    if (members.isEmpty()) {
                        Text("まだメンバーがいません", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    } else {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            members.forEach { m ->
                                FilterChip(selected = m.id == selectedId, onClick = { selectedId = m.id }, label = { Text(m.name) })
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
                                Text("${selected.name}さんとの1on1", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { removeMember(selected.id) }) { Text("メンバーを削除") }
                            }
                            NoteField("良かったこと・うまくいっていること", goodThings) { goodThings = it }
                            NoteField("気になっていること・課題", concerns) { concerns = it }
                            NoteField("次回までのアクション", nextActions) { nextActions = it }
                            Button(onClick = { addRecord(selected.id) }, modifier = Modifier.fillMaxWidth()) { Text("記録を保存") }
                        }
                    }
                }

                item {
                    Text("過去の記録", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (selected.records.isEmpty()) {
                    item { Text("まだ記録がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }

                items(selected.records, key = OneOnOneRecord::id) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(r.date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            r.displayLines().forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
}
