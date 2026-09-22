package com.ttech.dailyfortune.domain

import kotlinx.serialization.Serializable

@Serializable
data class DiaryEntry(
    val id: String,
    /** ISO8601 (YYYY-MM-DD) */
    val date: String,
    val text: String,
)
