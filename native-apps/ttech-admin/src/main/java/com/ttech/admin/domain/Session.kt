package com.ttech.admin.domain

import kotlinx.serialization.Serializable

/** ログイン中のセッション。expiresAtEpochSec は「アクセストークンの有効期限(UNIX秒)」 */
@Serializable
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSec: Long,
    val userId: String,
    val email: String?,
) {
    /** 期限切れ間近(または期限切れ)か。API呼び出しの直前に更新するかどうかの判定に使う */
    fun needsRefresh(nowEpochSec: Long, marginSec: Long = REFRESH_MARGIN_SEC): Boolean =
        expiresAtEpochSec - nowEpochSec <= marginSec

    companion object {
        const val REFRESH_MARGIN_SEC = 60L
    }
}
