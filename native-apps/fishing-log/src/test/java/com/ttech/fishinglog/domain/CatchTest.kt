package com.ttech.fishinglog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CatchTest {
    private fun c(id: String, species: String) = Catch(id, "2026-09-22", "堤防", species, null)

    @Test
    fun `魚種別の匹数を数える`() {
        val catches = listOf(c("1", "アジ"), c("2", "アジ"), c("3", "サバ"))
        val counts = catches.countBySpecies()
        assertEquals(2, counts.first { it.species == "アジ" }.count)
        assertEquals(1, counts.first { it.species == "サバ" }.count)
    }

    @Test
    fun `内訳は初めて登場した魚種の順に並ぶ`() {
        val catches = listOf(c("1", "サバ"), c("2", "アジ"), c("3", "サバ"), c("4", "イワシ"))
        val counts = catches.countBySpecies()
        assertEquals(listOf("サバ", "アジ", "イワシ"), counts.map { it.species })
    }

    @Test
    fun `記録がなければ内訳は空`() {
        assertEquals(emptyList<SpeciesCount>(), emptyList<Catch>().countBySpecies())
    }
}
