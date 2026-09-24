package com.ttech.admin.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ttech.admin.AdminContainer
import com.ttech.admin.data.AdminApiException
import com.ttech.admin.data.AuthApiException
import com.ttech.admin.data.SessionExpiredException
import com.ttech.admin.data.TotpRequiredException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val LocalContainer = compositionLocalOf<AdminContainer> { error("AdminContainer が提供されていません") }
val LocalActions = compositionLocalOf<Actions> { error("Actions が提供されていません") }

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Failed(val message: String) : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
}

class LoadHandle<T>(val state: LoadState<T>, val loading: Boolean, val reload: () -> Unit)

/** 画面に出す文言。管理APIのエラーはそのまま、それ以外は汎用の文 */
fun messageOf(e: Throwable): String = when (e) {
    is AdminApiException, is AuthApiException, is SessionExpiredException, is TotpRequiredException -> e.message ?: "エラーが発生しました"
    else -> "エラーが発生しました"
}

/**
 * サーバーから読み込んで表示するための共通処理。keys が変わるか reload() を呼ぶと読み直す。
 * 読み直し中も、前に表示していた内容は消さない(画面がちらつかない)。
 * 管理者として認められなかったとき(401)は、ログアウトしてログイン画面に戻す。
 */
@Composable
fun <T> rememberLoad(vararg keys: Any?, block: suspend () -> T): LoadHandle<T> {
    val container = LocalContainer.current
    var tick by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState<T>>(LoadState.Loading) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(*keys, tick) {
        loading = true
        try {
            state = LoadState.Ready(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is AdminApiException && e.status == 401) rejectAccess(container, "このアカウントには管理者権限がありません")
            if (e is TotpRequiredException) rejectAccess(container, messageOf(e))
            if (e is SessionExpiredException) container.notice.value = messageOf(e)
            state = LoadState.Failed(messageOf(e))
        } finally {
            loading = false
        }
    }
    return LoadHandle(state, loading) { tick++ }
}

/** サーバーに管理者と認められなかったとき: ログアウトして、理由をログイン画面に出す */
private suspend fun rejectAccess(container: AdminContainer, reason: String) {
    container.notice.value = reason
    container.auth.signOut()
}

/** ボタン操作(BAN・削除など)を実行し、結果をスナックバーで知らせる */
class Actions(private val scope: CoroutineScope, private val snackbar: SnackbarHostState, private val container: AdminContainer) {
    fun run(successMessage: String, onDone: () -> Unit = {}, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                onDone() // 結果の一覧を先に読み直す(スナックバーは表示が消えるまで待つので、後に置く)
                snackbar.showSnackbar(successMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is AdminApiException && e.status == 401) rejectAccess(container, "このアカウントには管理者権限がありません")
                if (e is SessionExpiredException) container.notice.value = messageOf(e)
                snackbar.showSnackbar(messageOf(e))
            }
        }
    }
}
