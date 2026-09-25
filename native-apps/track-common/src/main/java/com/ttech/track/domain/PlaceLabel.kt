package com.ttech.track.domain

/** 地名(住所)の表示用の整形 */
object PlaceLabel {
    /** 「日本、〒965-0000 福島県会津若松市…」→「福島県会津若松市…」 */
    fun shorten(addressLine: String?): String? {
        if (addressLine.isNullOrBlank()) return null
        var s = addressLine.trim()
        s = s.removePrefix("日本、").removePrefix("日本,").removePrefix("Japan, ").trim()
        s = s.replace(Regex("^〒?\\s*\\d{3}-?\\d{4}\\s*"), "")
        return s.ifBlank { null }
    }
}
