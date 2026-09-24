package com.ttech.sheetmusicviewer.domain

import android.view.KeyEvent
import kotlinx.serialization.Serializable

/** 読み込んだ楽譜(ページ順の画像URI)と、表示中のページ番号(0始まり)。 */
@Serializable
data class Score(val pages: List<String> = emptyList(), val index: Int = 0)

enum class PageTurn { NEXT, PREV }

/**
 * キーコードからページめくりの方向を決める。Webアプリ版の矢印キー(←→)・スペースキーに加え、
 * Bluetoothフットペダルがよく送る PageUp/PageDown・上下矢印にも対応する。
 */
fun pageTurnFor(keyCode: Int): PageTurn? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> PageTurn.NEXT
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_UP -> PageTurn.PREV
    else -> null
}

/** ページをめくる。最初・最後のページより先には進まない。 */
fun Score.turn(direction: PageTurn): Score = when (direction) {
    PageTurn.NEXT -> copy(index = minOf(index + 1, pages.lastIndex).coerceAtLeast(0))
    PageTurn.PREV -> copy(index = maxOf(index - 1, 0))
}

/** 「3 / 12」形式のページ表示。 */
fun Score.pageLabel(): String = "${index + 1} / ${pages.size}"

val Score.isFirst: Boolean get() = index == 0
val Score.isLast: Boolean get() = index >= pages.lastIndex
