package com.ttech.admin.domain

/** 報告に対してできる操作。id はサーバーAPIの action と同じ */
enum class ReportAction(val id: String, val label: String, val destructive: Boolean) {
    Dismiss("dismiss", "問題なし", false),
    DeleteContent("delete_content", "削除する", true),
    BanAuthor("ban_author", "投稿者をBAN", true),
}

object ReportRules {
    /** 報告が未対応か(日記: open / レビュー: open) */
    fun isOpen(status: String): Boolean = status == "open"

    /**
     * その報告に対して選べる操作。
     *  - 投稿・レビューが既に無ければ「削除する」は出さない
     *  - 投稿者のアカウントが既に削除されていれば「BAN」は出さない
     *  - 対応済みの報告には操作を出さない
     */
    fun availableActions(status: String, contentId: String?, targetUserId: String?): List<ReportAction> {
        if (!isOpen(status)) return emptyList()
        return buildList {
            add(ReportAction.Dismiss)
            if (contentId != null) add(ReportAction.DeleteContent)
            if (targetUserId != null) add(ReportAction.BanAuthor)
        }
    }

    /** 「削除する」の表示名。日記は削除、レビューは非表示 */
    fun deleteLabel(kind: String): String = if (kind == "review") "レビューを非表示にする" else "投稿を削除する"
}

object Totp {
    /** 認証アプリの6桁コード。空白やハイフンが混ざっていても受け付ける */
    fun normalize(input: String): String = input.filter { it.isDigit() }

    fun isValid(input: String): Boolean = normalize(input).length == 6
}
