package com.ttech.oneononelog.domain

import kotlinx.serialization.Serializable

@Serializable
data class OneOnOneRecord(
    val id: String,
    /** ISO8601 (YYYY-MM-DD) */
    val date: String,
    val goodThings: String,
    val concerns: String,
    val nextActions: String,
)

@Serializable
data class Member(
    val id: String,
    val name: String,
    /** 新しい記録が先頭 */
    val records: List<OneOnOneRecord> = emptyList(),
)

/** メンバーを末尾に追加する(Webアプリ版と同じく追加順に並ぶ)。 */
fun List<Member>.addMember(member: Member): List<Member> = this + member

fun List<Member>.removeMember(id: String): List<Member> = filterNot { it.id == id }

/** 指定メンバーの記録の先頭に [record] を足す。 */
fun List<Member>.addRecord(memberId: String, record: OneOnOneRecord): List<Member> =
    map { if (it.id == memberId) it.copy(records = listOf(record) + it.records) else it }

/** 入力値から記録を作る(各項目は前後の空白を除く)。 */
fun buildRecord(id: String, date: String, goodThings: String, concerns: String, nextActions: String): OneOnOneRecord =
    OneOnOneRecord(id, date, goodThings.trim(), concerns.trim(), nextActions.trim())

/** 記録の表示行。空の項目は出さない(Webアプリ版と同じ見出し)。 */
fun OneOnOneRecord.displayLines(): List<String> = listOfNotNull(
    goodThings.takeIf { it.isNotEmpty() }?.let { "良かったこと: $it" },
    concerns.takeIf { it.isNotEmpty() }?.let { "課題: $it" },
    nextActions.takeIf { it.isNotEmpty() }?.let { "次のアクション: $it" },
)
