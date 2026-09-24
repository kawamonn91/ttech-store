package com.ttech.kidsallowance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionTest {
    @Test
    fun `もらった記録は金額がプラス`() {
        val t = buildTransaction("1", "2026-09-24", " おこづかい ", "500", TransactionType.IN)
        assertEquals(Transaction("1", "2026-09-24", "おこづかい", 500), t)
    }

    @Test
    fun `つかった記録は金額がマイナス`() {
        assertEquals(-120L, buildTransaction("1", "2026-09-24", "おかし", "120", TransactionType.OUT)!!.amount)
    }

    @Test
    fun `金額が0や空、なにに?が空なら記録しない`() {
        assertNull(buildTransaction("1", "d", "おかし", "0", TransactionType.OUT))
        assertNull(buildTransaction("1", "d", "おかし", "", TransactionType.OUT))
        assertNull(buildTransaction("1", "d", "  ", "100", TransactionType.IN))
    }

    @Test
    fun `残高はすべての出入りの合計`() {
        val list = listOf(Transaction("1", "d", "a", 1000), Transaction("2", "d", "b", -300), Transaction("3", "d", "c", -50))
        assertEquals(650L, list.balance())
        assertEquals(0L, emptyList<Transaction>().balance())
    }

    @Test
    fun `金額表示はもらった分に+を付け3桁区切りにする`() {
        assertEquals("+1,000円", formatSignedYen(1000))
        assertEquals("-120円", formatSignedYen(-120))
        assertEquals("+0円", formatSignedYen(0))
    }
}
