package com.ttech.businesscardcontacts.domain

import com.ttech.businesscardcontacts.data.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VCardTest {
    private fun contact(
        name: String = "山田太郎",
        company: String = "テック株式会社",
        title: String = "エンジニア",
        phone: String = "090-1234-5678",
        email: String = "yamada@example.com",
    ) = Contact(id = "1", name = name, company = company, title = title, phone = phone, email = email)

    @Test
    fun `全項目入りの連絡先はvCard3_0形式に変換される`() {
        val vcard = contact().toVCard()
        assertEquals(
            listOf(
                "BEGIN:VCARD",
                "VERSION:3.0",
                "FN:山田太郎",
                "ORG:テック株式会社",
                "TITLE:エンジニア",
                "TEL;TYPE=WORK:090-1234-5678",
                "EMAIL:yamada@example.com",
                "END:VCARD",
            ).joinToString("\n"),
            vcard,
        )
    }

    @Test
    fun `電話番号とメールが空なら該当行を出力しない`() {
        val vcard = contact(phone = "", email = "").toVCard()
        assertFalse(vcard.contains("TEL"))
        assertFalse(vcard.contains("EMAIL"))
        assertEquals(
            listOf("BEGIN:VCARD", "VERSION:3.0", "FN:山田太郎", "ORG:テック株式会社", "TITLE:エンジニア", "END:VCARD")
                .joinToString("\n"),
            vcard,
        )
    }

    @Test
    fun `複数件はvCardを改行区切りで連結する`() {
        val combined = listOf(contact(name = "山田太郎"), contact(name = "鈴木花子")).toCombinedVCard()
        assertEquals(2, combined.split("BEGIN:VCARD").size - 1)
        assertEquals(2, combined.split("END:VCARD").size - 1)
    }

    @Test
    fun `ファイル名は氏名がベースでvcf拡張子になる`() {
        assertEquals("山田太郎.vcf", vcfFilename("山田太郎"))
    }

    @Test
    fun `氏名が空ならファイル名はcontact_vcfにフォールバックする`() {
        assertEquals("contact.vcf", vcfFilename(""))
    }
}
