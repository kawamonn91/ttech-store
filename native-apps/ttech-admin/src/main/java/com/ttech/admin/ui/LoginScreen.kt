package com.ttech.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ttech.admin.data.LoginResult
import kotlinx.coroutines.launch

/** メール+パスワードでログイン。2段階認証を設定済みなら、続けて6桁のコードを入力する */
@Composable
fun LoginScreen() {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val notice by container.notice.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var needsTotp by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun handle(result: LoginResult) {
        when (result) {
            is LoginResult.Success -> container.notice.value = null // 一覧の読み込みで管理者かどうかが確認される
            is LoginResult.NeedsTotp -> { needsTotp = true; error = null }
            is LoginResult.Failure -> error = result.message
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, alignment = androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text("T-tech 管理", style = MaterialTheme.typography.headlineSmall)
        Text("管理者のアカウントでログインしてください。", color = MaterialTheme.colorScheme.onSurfaceVariant)

        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (!needsTotp) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("メールアドレス") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("パスワード") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("認証アプリに表示されている6桁の確認コードを入力してください。")
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text("確認コード") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                busy = true
                error = null
                container.notice.value = null // 前回の知らせは、新しく試した時点で消す
                scope.launch {
                    val result = if (needsTotp) container.auth.submitTotp(code) else container.auth.signIn(email, password)
                    handle(result)
                    busy = false
                }
            },
            enabled = !busy && (if (needsTotp) code.length == 6 else email.isNotBlank() && password.isNotEmpty()),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp) else Text(if (needsTotp) "確認する" else "ログイン")
        }

        if (needsTotp) {
            TextButton(onClick = { container.auth.cancelPending(); needsTotp = false; code = ""; error = null }) { Text("最初からやり直す") }
        }
    }
}
