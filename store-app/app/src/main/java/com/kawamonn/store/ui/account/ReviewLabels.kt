package com.kawamonn.store.ui.account

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// Web の開発者ダッシュボード(web/src/lib/release-status.ts)と同じ言葉にそろえる。

/** アプリの状態(apps.status) */
fun appStatusLabel(status: String): String = when (status) {
    "draft" -> "非公開"
    "pending" -> "審査中"
    "published" -> "公開中"
    "suspended" -> "停止中"
    else -> status
}

/** リリースの状態(app_releases.status)。[verdict] は自動審査の判定(policy_verdict) */
fun releaseStateLabel(status: String, autoApproved: Boolean, verdict: String?): String = when (status) {
    "uploaded" -> "検査中"
    "scanned" -> if (verdict == "needs_review") "運営が確認中" else "承認待ち"
    "approved" -> "承認済み(非公開)"
    "published" -> if (autoApproved) "公開中(自動審査を通過)" else "公開中"
    "rejected" -> "公開できませんでした"
    else -> status
}

/**
 * 自動審査の結果(policy_findings。JSON配列)から、運営の確認が必要な理由(重大度が review・block のもの)の見出しを取り出す。
 * 参考情報(info)は含めない。形式が想定と違うときは、その要素を読み飛ばす(画面を壊さない)。
 */
fun reviewReasons(findings: JsonElement?, max: Int = 6): List<String> {
    val array = findings as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val severity = (obj["severity"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        if (severity != "review" && severity != "block") return@mapNotNull null
        (obj["title"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }.distinct().take(max)
}

/** 自分のアプリの最新のリリース(作成日時が新しいもの)。日時はISO形式なので、文字列の比較で並べられる */
fun <T> newestRelease(releases: List<T>, createdAt: (T) -> String): T? = releases.maxByOrNull(createdAt)
