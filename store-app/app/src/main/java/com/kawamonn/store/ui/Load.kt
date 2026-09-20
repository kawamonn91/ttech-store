package com.kawamonn.store.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.kawamonn.store.AppContainer
import com.kawamonn.store.data.api.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String) : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
}

/** 1回の非同期取得の状態(読み込み中/失敗/成功)を持つ ViewModel の土台 */
abstract class LoadViewModel<T> : ViewModel() {
    private val _state = MutableStateFlow<Load<T>>(Load.Loading)
    val state: StateFlow<Load<T>> = _state.asStateFlow()

    protected abstract suspend fun fetch(): T

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch {
            _state.value = try {
                Load.Ready(fetch())
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                Load.Failed(e.message ?: "読み込みに失敗しました")
            } catch (e: Exception) {
                Load.Failed("読み込みに失敗しました")
            }
        }
    }
}

/** AppContainer を受け取って ViewModel を作るファクトリ */
fun <VM : ViewModel> factory(container: AppContainer, create: (AppContainer) -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = create(container) as T
    }
