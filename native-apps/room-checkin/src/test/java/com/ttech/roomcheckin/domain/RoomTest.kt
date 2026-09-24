package com.ttech.roomcheckin.domain

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomTest {
    private val rooms = listOf(Room("1", "会議室A"), Room("2", "会議室B"))

    @Test
    fun `会議室は登録順に並び削除できる`() {
        val next = rooms.addRoom(Room("3", "席C"))
        assertEquals(listOf("1", "2", "3"), next.map { it.id })
        assertEquals(listOf("1", "3"), next.removeRoom("2").map { it.id })
    }

    @Test
    fun `チェックインで利用者と時刻を記録する`() {
        val next = rooms.checkIn("1", " 山田 ", 1_000L)
        assertEquals(Room("1", "会議室A", "山田", 1_000L), next[0])
        assertEquals(Room("2", "会議室B"), next[1])
    }

    @Test
    fun `利用者名が空なら利用者として記録する`() {
        assertEquals("利用者", rooms.checkIn("1", "  ", 1_000L)[0].occupiedBy)
    }

    @Test
    fun `チェックアウトで空室に戻る`() {
        val room = rooms.checkIn("1", "山田", 1_000L).checkOut("1")[0]
        assertNull(room.occupiedBy)
        assertNull(room.checkedInAt)
    }

    @Test
    fun `状態の表示`() {
        assertEquals("空室", Room("1", "A").statusLabel())
        assertEquals("山田が使用中", Room("1", "A", "山田", 1L).statusLabel())
    }

    @Test
    fun `QRコードを読み取るとroomと会議室名になる`() {
        val room = Room("1", "会議室A")
        val matrix = encodeQr(room.qrContent())!!
        val source = BitMatrixSource(matrix)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)))
        assertEquals("room:会議室A", decoded.text)
    }
}

/** 生成したQR行列をそのまま読み取りにかけるための画像ソース(白=255、黒=0)。 */
private class BitMatrixSource(private val matrix: BitMatrix) : LuminanceSource(matrix.width, matrix.height) {
    override fun getRow(y: Int, row: ByteArray?): ByteArray =
        ByteArray(width) { x -> if (matrix.get(x, y)) 0 else 255.toByte() }

    override fun getMatrix(): ByteArray =
        ByteArray(width * height) { i -> if (matrix.get(i % width, i / width)) 0 else 255.toByte() }
}
