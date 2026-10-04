package com.ttech.bikenavi.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Open-Meteo の予報(hourly)の応答を [HourlyForecast] にする。
 * 地点を1つだけ頼んだときは1つのオブジェクト、複数を頼んだときは配列(頼んだ順)で返ってくる。
 */
object OpenMeteoParser {
    fun parse(text: String): List<HourlyForecast> {
        val root = try {
            BikeJson.parseToJsonElement(text)
        } catch (e: Exception) {
            throw BikeException("天気予報の応答を読み取れませんでした", e)
        }
        val items: List<JsonObject> = when (root) {
            is JsonArray -> root.map { it.jsonObject }
            is JsonObject -> listOf(root)
            else -> throw BikeException("天気予報の応答を読み取れませんでした")
        }
        return items.map { obj ->
            val hourly = obj["hourly"]?.jsonObject ?: throw BikeException("天気予報を取得できませんでした")
            val times = hourly["time"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            HourlyForecast.of(
                times = times,
                codes = ints(hourly["weather_code"]),
                pops = ints(hourly["precipitation_probability"]),
                precips = doubles(hourly["precipitation"]),
                temps = doubles(hourly["temperature_2m"]),
            )
        }
    }

    private fun ints(e: JsonElement?): List<Int?> = e?.jsonArray?.map { if (it is JsonNull) null else it.jsonPrimitive.intOrNull ?: it.jsonPrimitive.doubleOrNull?.toInt() } ?: emptyList()

    private fun doubles(e: JsonElement?): List<Double?> = e?.jsonArray?.map { if (it is JsonNull) null else it.jsonPrimitive.doubleOrNull } ?: emptyList()
}
