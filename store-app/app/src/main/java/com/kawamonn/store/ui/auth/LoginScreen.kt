package com.kawamonn.store.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kawamonn.store.auth.AuthRepository
import com.kawamonn.store.auth.GoogleSignInHelper
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(auth: AuthRepository, onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleHelper = remember { GoogleSignInHelper(context, com.kawamonn.store.BuildConfig.GOOGLE_WEB_CLIENT_ID) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun withGoogle() {
        error = null
        busy = true
        scope.launch {
            runCatching { googleHelper.getIdToken() }
                .onSuccess { result ->
                    if (result == null) {
                        busy = false
                        return@onSuccess
                    }
                    auth.signInWithGoogleIdToken(result.idToken, result.nonce)
                        .onSuccess { busy = false; onSignedIn() }
                        .onFailure { busy = false; error = it.message ?: "ログインに失敗しました" }
                }
                .onFailure { busy = false; error = it.message ?: "Googleログインを開始できませんでした" }
        }
    }

    fun withPassword() {
        error = null
        busy = true
        scope.launch {
            auth.signInWithPassword(email, password)
                .onSuccess { busy = false; onSignedIn() }
                .onFailure { busy = false; error = "メールアドレスまたはパスワードが正しくありません" }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("ログイン", style = MaterialTheme.typography.headlineSmall)
        Text(
            "ログインすると、マイページで自分のアプリの状態や更新履歴を確認できます。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )

        OutlinedButton(onClick = ::withGoogle, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Googleでログイン")
        }

        Text(
            "または",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("メールアドレス") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("パスワード") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = ::withPassword,
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(if (busy) "処理中…" else "ログイン")
        }
    }
}
