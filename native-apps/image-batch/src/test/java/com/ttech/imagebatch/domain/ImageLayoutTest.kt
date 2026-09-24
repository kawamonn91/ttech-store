package com.ttech.imagebatch.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageLayoutTest {

    @Test
    fun `正方形の画像を正方形キャンバスに収めると全面に配置される`() {
        val rect = computeDrawRect(srcWidth = 400, srcHeight = 400, targetWidth = 800, targetHeight = 800)
        assertEquals(0f, rect.x, 0.01f)
        assertEquals(0f, rect.y, 0.01f)
        assertEquals(800f, rect.width, 0.01f)
        assertEquals(800f, rect.height, 0.01f)
    }

    @Test
    fun `横長の画像は幅基準で縮小され上下に余白ができる`() {
        // 元画像 2000x1000(横長)を 800x800 の正方形キャンバスに収める
        val rect = computeDrawRect(srcWidth = 2000, srcHeight = 1000, targetWidth = 800, targetHeight = 800)
        assertEquals(800f, rect.width, 0.01f)
        assertEquals(400f, rect.height, 0.01f)
        assertEquals(0f, rect.x, 0.01f)
        assertEquals(200f, rect.y, 0.01f)
    }

    @Test
    fun `縦長の画像は高さ基準で縮小され左右に余白ができる`() {
        // 元画像 1000x2000(縦長)を 800x800 の正方形キャンバスに収める
        val rect = computeDrawRect(srcWidth = 1000, srcHeight = 2000, targetWidth = 800, targetHeight = 800)
        assertEquals(400f, rect.width, 0.01f)
        assertEquals(800f, rect.height, 0.01f)
        assertEquals(200f, rect.x, 0.01f)
        assertEquals(0f, rect.y, 0.01f)
    }

    @Test
    fun `縦長キャンバスに正方形画像を収めると上下に余白ができる`() {
        val rect = computeDrawRect(srcWidth = 1000, srcHeight = 1000, targetWidth = 1080, targetHeight = 1350)
        assertEquals(1080f, rect.width, 0.01f)
        assertEquals(1080f, rect.height, 0.01f)
        assertEquals(0f, rect.x, 0.01f)
        assertEquals(135f, rect.y, 0.01f)
    }
}
