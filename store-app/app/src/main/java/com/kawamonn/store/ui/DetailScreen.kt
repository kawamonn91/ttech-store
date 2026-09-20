package com.kawamonn.store.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kawamonn.store.data.api.AppDetailDto
import com.kawamonn.store.data.api.StoreApi
import com.kawamonn.store.util.formatBytes
import com.kawamonn.store.util.formatCount
import com.kawamonn.store.util.shortPermission

class DetailViewModel(private val api: StoreApi, private val slug: String) : LoadViewModel<AppDetailDto>() {
    init {
        load()
    }

    override suspend fun fetch(): AppDetailDto = api.app(slug)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(slug: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val vm: DetailViewModel = viewModel(key = "detail-$slug", factory = factory(container) { DetailViewModel(it.api, slug) })
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state as? Load.Ready)?.data?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            LoadContent(state, onRetry = vm::load) { app -> DetailBody(app) }
        }
    }
}

@Composable
private fun DetailBody(app: AppDetailDto) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.iconUrl, 84.dp)
            Column {
                Text(app.name, style = MaterialTheme.typography.headlineSmall)
                if (app.developerName.isNotBlank()) {
                    Text(app.developerName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RatingLabel(app.ratingAvg, app.ratingCount)
                    Text("${formatCount(app.downloadCount)} DL", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        InstallButton(app.packageName, app.latest, Modifier.padding(horizontal = 16.dp))

        if (app.screenshots.isNotEmpty()) {
            LazyRow(
                Modifier.padding(top = 16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(app.screenshots) { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = "スクリーンショット",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(320.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
        }

        SectionTitle("アプリについて", Modifier.padding(top = 8.dp))
        Text(
            app.description.ifBlank { app.shortDesc },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        app.latest?.let { latest ->
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            SectionTitle("最新バージョン ${latest.versionName}")
            if (latest.releaseNotes.isNotBlank()) {
                Text(latest.releaseNotes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                latest.apkSize?.let { InfoRow("サイズ", formatBytes(it)) }
                latest.minSdk?.let { InfoRow("必要なOS", "Android ${androidVersionName(it)} 以上") }
                app.category?.let { InfoRow("カテゴリ", it.name) }
                if (latest.permissions.isNotEmpty()) {
                    InfoRow("必要な権限", latest.permissions.joinToString(", ") { shortPermission(it) })
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.3f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.7f))
    }
}

/** API レベル → 表示用の Android バージョン(主要なもののみ。不明なら API レベルをそのまま出す) */
internal fun androidVersionName(sdk: Int): String = when (sdk) {
    26 -> "8.0"; 27 -> "8.1"; 28 -> "9"; 29 -> "10"; 30 -> "11"; 31 -> "12"; 32 -> "12L"
    33 -> "13"; 34 -> "14"; 35 -> "15"; 36 -> "16"
    else -> "(API $sdk)"
}
