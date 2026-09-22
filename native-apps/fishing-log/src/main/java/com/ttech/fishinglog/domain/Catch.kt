package com.ttech.fishinglog.domain

import kotlinx.serialization.Serializable

@Serializable
data class Catch(
    val id: String,
    /** ISO8601 (YYYY-MM-DD) */
    val date: String,
    val location: String,
    val species: String,
    val sizeCm: Double?,
)

data class SpeciesCount(val species: String, val count: Int)

/**
 * 魚種別の匹数。Webアプリ版(JSオブジェクトのキー順 = 初めて登場した順)と同じ順序で返す。
 */
fun List<Catch>.countBySpecies(): List<SpeciesCount> {
    val counts = LinkedHashMap<String, Int>()
    for (c in this) counts[c.species] = (counts[c.species] ?: 0) + 1
    return counts.map { (species, count) -> SpeciesCount(species, count) }
}
