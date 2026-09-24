package com.ttech.pdftoolkit.domain

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PdfToolsTest {
    /** ページごとに幅を変えた(=どのページか見分けられる)PDFを作る。 */
    private fun pdf(vararg widths: Float): ByteArray = PDDocument().use { doc ->
        widths.forEach { doc.addPage(PDPage(PDRectangle(it, 100f))) }
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    private fun widths(bytes: ByteArray): List<Float> =
        PDDocument.load(bytes).use { doc -> doc.pages.map { it.mediaBox.width } }

    @Test
    fun `ページ指定を0始まりの番号にする`() {
        assertEquals(listOf(0, 1, 2, 4), parseRange("1-3,5", 10))
        assertEquals(listOf(1), parseRange(" 2 ", 10))
    }

    @Test
    fun `ページ数を超える指定・0ページ・読めない部分は含めない`() {
        assertEquals(listOf(7, 8, 9), parseRange("8-12", 10))
        assertEquals(listOf(0), parseRange("0-1", 10))
        assertEquals(listOf(2), parseRange("a,3,1-x,", 10))
        assertEquals(emptyList<Int>(), parseRange("11", 10))
    }

    @Test
    fun `指定した順と重複はそのまま残す`() {
        assertEquals(listOf(4, 0, 0), parseRange("5,1,1", 10))
    }

    @Test
    fun `ページ数を数える`() {
        assertEquals(3, pageCount(ByteArrayInputStream(pdf(100f, 200f, 300f))))
    }

    @Test
    fun `複数のPDFを渡した順に結合する`() {
        val out = ByteArrayOutputStream()
        mergePdfs(listOf(ByteArrayInputStream(pdf(100f, 200f)), ByteArrayInputStream(pdf(300f))), out)
        assertEquals(listOf(100f, 200f, 300f), widths(out.toByteArray()))
    }

    @Test
    fun `指定したページだけを指定した順に抜き出す`() {
        val out = ByteArrayOutputStream()
        extractPages(ByteArrayInputStream(pdf(100f, 200f, 300f, 400f)), listOf(3, 0), out)
        assertEquals(listOf(400f, 100f), widths(out.toByteArray()))
    }
}
