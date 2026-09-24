package com.ttech.track.data

import com.ttech.track.domain.TrackCodec
import com.ttech.track.domain.TrackPoint
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/**
 * 記録ごとのファイル置き場。1回の記録 = 概要(JSON)・点の並び(CSV)・地図の画像(JPEG)の3つ。
 * 概要の中身(ドライブ用・ランニング用)はアプリごとに違うので、ここでは文字列のまま扱う。
 *
 * 点は追記だけで書くので、記録の途中でアプリが止まっても、それまでの分が残る。
 * 概要は「一時ファイルに書いてから置き換える」ので、書き込み中に止まっても壊れた概要は残らない。
 */
class TrackFiles(private val dir: File) {
    init {
        dir.mkdirs()
    }

    fun csvFile(id: String) = File(dir, "${safe(id)}.csv")
    fun metaFile(id: String) = File(dir, "${safe(id)}.json")
    fun imageFile(id: String) = File(dir, "${safe(id)}.jpg")

    fun openWriter(id: String): TrackWriter = TrackWriter(csvFile(id))

    fun readTrack(id: String): List<TrackPoint> {
        val file = csvFile(id)
        if (!file.exists()) return emptyList()
        return file.bufferedReader().useLines { TrackCodec.decodeAll(it) }
    }

    fun writeMeta(id: String, text: String) {
        val target = metaFile(id)
        val tmp = File(dir, "${safe(id)}.json.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }

    fun readMeta(id: String): String? = metaFile(id).takeIf { it.exists() }?.readText()

    /** 記録のID(新しい順)。概要か点のファイルがあるものすべて */
    fun ids(): List<String> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && (it.name.endsWith(".json") || it.name.endsWith(".csv")) }
            .map { it.name.substringBeforeLast('.') }
            .distinct()
            .sortedDescending()

    fun hasImage(id: String): Boolean = imageFile(id).let { it.exists() && it.length() > 0 }

    fun delete(id: String) {
        csvFile(id).delete()
        metaFile(id).delete()
        imageFile(id).delete()
    }

    /** ID はこのアプリが作る数字の文字列だけ。パスの区切りなどが混ざる(別の場所を指す)ことを防ぐ */
    private fun safe(id: String): String {
        require(id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '_' || it == '-' }) { "不正なID: $id" }
        return id
    }
}

/** 点を1行ずつ追記する。一定数ごとに書き出して、途中で止まっても失われる分を少なくする */
class TrackWriter(file: File, private val flushEvery: Int = 5) : Closeable {
    private val out: BufferedWriter = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))
    var count: Int = 0
        private set

    @Synchronized
    fun append(p: TrackPoint) {
        out.write(TrackCodec.encode(p))
        out.newLine()
        count++
        if (count % flushEvery == 0) out.flush()
    }

    @Synchronized
    fun flush() = out.flush()

    @Synchronized
    override fun close() {
        runCatching { out.flush() }
        runCatching { out.close() }
    }
}
