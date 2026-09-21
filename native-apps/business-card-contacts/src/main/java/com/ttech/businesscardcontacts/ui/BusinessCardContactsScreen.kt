package com.ttech.businesscardcontacts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttech.businesscardcontacts.data.Contact
import com.ttech.businesscardcontacts.data.ContactStore
import com.ttech.businesscardcontacts.domain.toCombinedVCard
import com.ttech.businesscardcontacts.domain.toVCard
import com.ttech.businesscardcontacts.domain.vcfFilename
import com.ttech.common.share.shareTextFile
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BusinessCardContactsScreen() {
    val context = LocalContext.current
    val store = remember { ContactStore(context) }
    val scope = rememberCoroutineScope()
    val contacts by store.contacts.collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    fun addContact() {
        if (name.isBlank()) return
        // 先に値を確定させてからlaunchに渡す。scope.launch { name.trim() ... } のように
        // ラムダの中で状態を読むと、コルーチンの実際の開始タイミング次第では直後の
        // クリア処理(name = "" など)より後に評価されてしまい、空文字が保存される
        // ことがあるため。
        val contact = Contact(
            id = System.nanoTime().toString(),
            name = name.trim(),
            company = company.trim(),
            title = title.trim(),
            phone = phone.trim(),
            email = email.trim(),
        )
        scope.launch { store.add(contact) }
        name = ""; company = ""; title = ""; phone = ""; email = ""
    }

    Scaffold(topBar = { TopAppBar(title = { Text("名刺コンタクト") }) }) { padding ->
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
                                value = company,
                                onValueChange = { company = it },
                                label = { Text("会社名") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("役職") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("電話番号") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("メール") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Button(
                            onClick = ::addContact,
                            enabled = name.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("連絡先を追加") }
                    }
                }
            }

            if (contacts.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = {
                            shareTextFile(
                                context = context,
                                content = contacts.toCombinedVCard(),
                                filename = "contacts.vcf",
                                mimeType = "text/x-vcard",
                                chooserTitle = "連絡先を共有",
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("全${contacts.size}件をまとめてvCard共有") }
                }
            }

            item {
                Text(
                    "連絡先一覧",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (contacts.isEmpty()) {
                item { Text("まだ連絡先がありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(contacts, key = Contact::id) { contact ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                            val sub = listOfNotNull(
                                contact.company.takeIf { it.isNotBlank() },
                                contact.title.takeIf { it.isNotBlank() },
                            ).joinToString(" ・ ")
                            if (sub.isNotEmpty()) {
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row {
                            TextButton(onClick = {
                                shareTextFile(
                                    context = context,
                                    content = contact.toVCard(),
                                    filename = vcfFilename(contact.name),
                                    mimeType = "text/x-vcard",
                                    chooserTitle = "連絡先を共有",
                                )
                            }) { Text("vCard") }
                            TextButton(onClick = { scope.launch { store.remove(contact.id) } }) { Text("削除") }
                        }
                    }
                }
            }
        }
    }
}
