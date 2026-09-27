package com.ttech.ideamemo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttech.ideamemo.domain.Idea
import com.ttech.ideamemo.share.shareIdea

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IdeaDetailSheet(idea: Idea, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(idea.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Text(idea.status.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(idea.category.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("作りたい度", style = MaterialTheme.typography.bodyMedium)
                Stars(idea.priority, size = 20.dp)
            }
            if (idea.oneLiner.isNotEmpty()) Text(idea.oneLiner, style = MaterialTheme.typography.bodyLarge)
            if (idea.memo.isNotEmpty()) Text(idea.memo, style = MaterialTheme.typography.bodyMedium)
            if (idea.reference.isNotEmpty()) Text("きっかけ: ${idea.reference}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text("編集") }
                TextButton(onClick = { shareIdea(context, idea) }) { Text("共有") }
                TextButton(onClick = { confirmDelete = true }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "削除しますか?",
            message = "「${idea.title}」を削除します。元に戻せません。",
            confirmLabel = "削除",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

fun Idea.summaryLine(): String = listOf(category.label, oneLiner).filter { it.isNotEmpty() }.joinToString(" ・ ")
