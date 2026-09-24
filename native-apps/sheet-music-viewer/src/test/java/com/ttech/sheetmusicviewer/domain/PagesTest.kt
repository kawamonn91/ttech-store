package com.ttech.sheetmusicviewer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PagesTest {

    @Test
    fun `次へは最後のページで止まる`() {
        assertEquals(1, nextPageIndex(0, pageCount = 3))
        assertEquals(2, nextPageIndex(1, pageCount = 3))
        assertEquals(2, nextPageIndex(2, pageCount = 3))
    }

    @Test
    fun `前へは最初のページで止まる`() {
        assertEquals(1, prevPageIndex(2))
        assertEquals(0, prevPageIndex(1))
        assertEquals(0, prevPageIndex(0))
    }

    @Test
    fun `1ページだけなら前へも次へも動かない`() {
        assertEquals(0, nextPageIndex(0, pageCount = 1))
        assertEquals(0, prevPageIndex(0))
    }

    @Test
    fun `ページ表示は1始まり`() {
        assertEquals("1 / 5", pageLabel(0, 5))
        assertEquals("5 / 5", pageLabel(4, 5))
    }

    @Test
    fun `画面の左半分は前へ右半分は次へ`() {
        assertEquals(PageTurn.PREV, turnForTap(x = 100f, width = 1080f))
        assertEquals(PageTurn.NEXT, turnForTap(x = 700f, width = 1080f))
    }

    @Test
    fun `ちょうど中央は次へ`() {
        assertEquals(PageTurn.NEXT, turnForTap(x = 540f, width = 1080f))
    }

    @Test
    fun `小さい画像は縮小しない`() {
        assertEquals(1, calcSampleSize(1000, 1400, maxDimension = 2400))
    }

    @Test
    fun `大きい画像は画面解像度を下回らない範囲で縮小する`() {
        // 長辺6000pxを2400px以上に保てる最大の縮小は1/2(3000px)。1/4だと1500pxで足りない。
        assertEquals(2, calcSampleSize(4500, 6000, maxDimension = 2400))
        // 長辺12000pxなら1/4(3000px)まで縮小できる。
        assertEquals(4, calcSampleSize(9000, 12000, maxDimension = 2400))
    }

    @Test
    fun `縦長でも横長でも長辺で判断する`() {
        assertEquals(calcSampleSize(6000, 4500, 2400), calcSampleSize(4500, 6000, 2400))
    }

    @Test
    fun `EXIFの向きから回転角を求める`() {
        assertEquals(0, exifRotationDegrees(1))
        assertEquals(90, exifRotationDegrees(6))
        assertEquals(180, exifRotationDegrees(3))
        assertEquals(270, exifRotationDegrees(8))
        assertEquals(0, exifRotationDegrees(0))
    }
}
