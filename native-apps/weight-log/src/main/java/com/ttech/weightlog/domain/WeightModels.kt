package com.ttech.weightlog.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 1日ぶんの記録。同じ日付は1件まで(あとから記録すると上書きする) */
@Serializable
data class WeightEntry(
    val id: String,
    /** ISO(yyyy-MM-dd) */
    val date: String,
    val weightKg: Double,
    /** 体脂肪率(%)。未入力なら null */
    val bodyFatPercent: Double? = null,
    val memo: String = "",
    val createdAtMs: Long = 0,
    val updatedAtMs: Long = 0,
)

/** 身長・目標(端末に1つだけ保存する) */
@Serializable
data class Profile(
    /** 身長(cm)。未入力なら null */
    val heightCm: Double? = null,
    /** 目標体重(kg)。未入力なら null */
    val goalWeightKg: Double? = null,
    /** 目標の日付(ISO)。未入力なら null */
    val goalDate: String? = null,
)

val WeightJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}
