package com.kawamonn.store.update

import com.kawamonn.store.data.api.StoreApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 「今インストール済みのアプリに更新があるか」を保持する、アプリ全体で共有の状態。
 *
 * 定期チェック(12時間ごとの [UpdateWorker])とは別に、ストアアプリを開くたび
 * (ホーム画面表示・フォアグラウンド復帰のたび)に最新化する。ホーム画面のバナーと
 * 下部ナビの「アップデート」タブのバッジは、両方ともこの状態を見て表示する。
 */
class UpdateChecker(
    private val api: StoreApi,
    /** packageName からインストール済み versionCode を引く(未インストールなら null)。テストしやすいようラムダで受け取る */
    private val installedVersion: (String) -> Long?,
    private val scope: CoroutineScope,
) {
    private val _updates = MutableStateFlow<List<AvailableUpdate>>(emptyList())
    val updates: StateFlow<List<AvailableUpdate>> = _updates.asStateFlow()

    /** ホーム画面の更新バナーを閉じたら true。次に更新内容が変わったら(バッジは残るが)バナーは再度出さない仕組みは持たず、単純に「今回のセッションで閉じたか」だけ覚える */
    private val _bannerDismissed = MutableStateFlow(false)
    val bannerDismissed: StateFlow<Boolean> = _bannerDismissed.asStateFlow()

    fun dismissBanner() {
        _bannerDismissed.value = true
    }

    /** ストアアプリを開いた/フォアグラウンドに戻ったタイミングで呼ぶ */
    fun refresh() {
        scope.launch {
            // api.index() 自身が内部で Dispatchers.IO に切り替えるので、ここで二重にしない
            // (テストで TestScope の仮想時間制御が効かなくなるのを避ける意味もある)
            val index = try {
                api.index().items
            } catch (_: Exception) {
                return@launch // オフライン等。前回の結果を保持したままにする
            }
            val fresh = UpdateDetector.detect(index) { installedVersion(it) }
            if (fresh.map { it.entry.packageName }.toSet() != _updates.value.map { it.entry.packageName }.toSet()) {
                _bannerDismissed.value = false // 新しく更新が出てきたら、バナーをもう一度見せる
            }
            _updates.value = fresh
        }
    }
}
