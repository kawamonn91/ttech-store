package com.ttech.pomodorotimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ttech.pomodorotimer.data.SessionCountStore
import com.ttech.pomodorotimer.domain.Phase
import com.ttech.pomodorotimer.domain.PomodoroTimer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen() {
    val context = LocalContext.current
    val vm: PomodoroViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            PomodoroViewModel(SessionCountStore(context)) as T
    })
    val state by vm.state.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("ポモドーロタイマー") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (state.phase == Phase.FOCUS) "集中タイム" else "休憩タイム",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        PomodoroTimer.formatTime(state.secondsLeft),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = vm::toggleRunning) {
                            Icon(if (state.running) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(if (state.running) "一時停止" else "開始", modifier = Modifier.padding(start = 4.dp))
                        }
                        OutlinedButton(onClick = vm::reset) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("リセット", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("時間の設定(分)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DurationField("集中", state.focusMinutes, Modifier.weight(1f)) { vm.applyDurations(it, state.breakMinutes) }
                        DurationField("休憩", state.breakMinutes, Modifier.weight(1f)) { vm.applyDurations(state.focusMinutes, it) }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("これまでに完了した集中セッション", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${state.completedFocusSessions}回", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun DurationField(label: String, value: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { text -> text.toIntOrNull()?.takeIf { it in 1..999 }?.let(onChange) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}
