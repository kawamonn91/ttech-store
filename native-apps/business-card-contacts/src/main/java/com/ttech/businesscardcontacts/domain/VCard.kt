package com.ttech.businesscardcontacts.domain

import com.ttech.businesscardcontacts.data.Contact

/** 1件の連絡先をvCard 3.0形式のテキストに変換する(Webアプリ版と同じロジック)。 */
fun Contact.toVCard(): String =
    listOf(
        "BEGIN:VCARD",
        "VERSION:3.0",
        "FN:$name",
        "ORG:$company",
        "TITLE:$title",
        phone.takeIf { it.isNotBlank() }?.let { "TEL;TYPE=WORK:$it" } ?: "",
        email.takeIf { it.isNotBlank() }?.let { "EMAIL:$it" } ?: "",
        "END:VCARD",
    ).filter { it.isNotEmpty() }.joinToString("\n")

/** 複数件をまとめて1つのvCardテキストに連結する(全件まとめて共有する用)。 */
fun List<Contact>.toCombinedVCard(): String = joinToString("\n") { it.toVCard() }

/** 共有・保存時のファイル名(氏名が空なら "contact.vcf" にフォールバック、Webアプリ版と同じ)。 */
fun vcfFilename(name: String): String = "${name.ifBlank { "contact" }}.vcf"
