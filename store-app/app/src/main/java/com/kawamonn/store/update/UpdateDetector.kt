package com.kawamonn.store.update

import com.kawamonn.store.data.api.IndexEntryDto

data class AvailableUpdate(val entry: IndexEntryDto, val installedVersionCode: Long)

object UpdateDetector {
    /**
     * カタログの全件インデックスと、端末のインストール状況を突き合わせて更新対象を返す。
     * [installedVersion] は未インストールなら null。この判定は端末内で完結し、
     * インストール済みアプリの一覧はサーバーに送らない。
     */
    fun detect(index: List<IndexEntryDto>, installedVersion: (String) -> Long?): List<AvailableUpdate> =
        index.mapNotNull { entry ->
            val installed = installedVersion(entry.packageName) ?: return@mapNotNull null
            if (entry.latest.versionCode > installed) AvailableUpdate(entry, installed) else null
        }
}
