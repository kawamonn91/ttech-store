package com.kawamonn.store.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// サーバーのエラー応答など、決まった型に読み込まない JSON から項目を取り出すための道具。

/** 本文を JSON のオブジェクトとして読む。JSON でない・オブジェクトでないときは null */
fun Json.parseObjectOrNull(text: String): JsonObject? = runCatching { parseToJsonElement(text) as? JsonObject }.getOrNull()

/** 文字列(または数値・真偽値)の項目の値。無い・null・オブジェクトや配列のときは null */
fun JsonObject?.stringField(name: String): String? = (this?.get(name) as? JsonPrimitive)?.contentOrNull
