package com.ttech.pocketdev.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.ttech.pocketdev.ConnectionTarget
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.Vector

/**
 * SFTP で取得したファイルエントリ。
 */
data class RemoteFileEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val mtime: Long,
)

/**
 * JSch を用いた SSH/SFTP クライアント。
 */
class SshClient(private val target: ConnectionTarget) : AutoCloseable {
    private var session: Session? = null
    private var sftpChannel: ChannelSftp? = null

    val isConnected: Boolean get() = session?.isConnected == true

    /**
     * SSH セッションと SFTP チャンネルを接続する。
     */
    @Synchronized
    fun connect(timeoutMs: Int = 15000) {
        if (isConnected) return

        val jsch = JSch()
        val user = target.sshUser.ifBlank { "root" }
        val host = target.sshHost
        val port = if (target.sshPort in 1..65535) target.sshPort else 22

        if (target.sshAuthMethod == ConnectionTarget.SshAuthMethod.KEY && target.sshPrivateKey.isNotBlank()) {
            val keyBytes = target.sshPrivateKey.toByteArray(Charsets.UTF_8)
            val passBytes = if (target.sshPassphrase.isNotBlank()) {
                target.sshPassphrase.toByteArray(Charsets.UTF_8)
            } else null
            jsch.addIdentity("custom-key", keyBytes, null, passBytes)
        }

        val sess = jsch.getSession(user, host, port)
        if (target.sshAuthMethod == ConnectionTarget.SshAuthMethod.PASSWORD && target.sshPassword.isNotBlank()) {
            sess.setPassword(target.sshPassword)
        }

        val config = Properties().apply {
            put("StrictHostKeyChecking", "no")
            put("PreferredAuthentications", "publickey,password,keyboard-interactive")
        }
        sess.setConfig(config)
        sess.timeout = timeoutMs
        sess.connect(timeoutMs)

        val sftp = sess.openChannel("sftp") as ChannelSftp
        sftp.connect(timeoutMs)

        session = sess
        sftpChannel = sftp
    }

    private fun ensureConnected(): ChannelSftp {
        if (!isConnected || sftpChannel?.isConnected != true) {
            connect()
        }
        return sftpChannel ?: throw IllegalStateException("SFTP 接続が確立されていません")
    }

    /** リモートの基準ディレクトリを考慮した絶対パスに変換する */
    fun resolveRemotePath(relPath: String): String {
        val root = target.sshRemoteRoot.trim().ifBlank { "." }
        val cleanRel = relPath.trim().trimStart('/')
        if (cleanRel.isEmpty()) return root
        return if (root.endsWith("/")) "$root$cleanRel" else "$root/$cleanRel"
    }

    /**
     * 指定パスのディレクトリ一覧を取得する。
     */
    fun listDir(relPath: String): List<RemoteFileEntry> {
        val sftp = ensureConnected()
        val fullPath = resolveRemotePath(relPath)
        val entries = mutableListOf<RemoteFileEntry>()

        @Suppress("UNCHECKED_CAST")
        val vector = sftp.ls(fullPath) as Vector<ChannelSftp.LsEntry>

        for (entry in vector) {
            val name = entry.filename
            if (name == "." || name == "..") continue
            val attrs = entry.attrs
            val isDir = attrs.isDir
            val size = if (isDir) 0L else attrs.size
            val mtime = attrs.mTime.toLong()
            val childRel = if (relPath.isBlank() || relPath == ".") name else "${relPath.trimEnd('/')}/$name"
            entries.add(RemoteFileEntry(name, childRel, isDir, size, mtime))
        }

        entries.sortWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
        return entries
    }

    /**
     * テキストファイルの内容を読み出す。
     */
    fun readText(relPath: String): String {
        val bytes = readBytes(relPath)
        return String(bytes, Charsets.UTF_8)
    }

    fun readBytes(relPath: String): ByteArray {
        val sftp = ensureConnected()
        val fullPath = resolveRemotePath(relPath)
        val out = ByteArrayOutputStream()
        sftp.get(fullPath, out)
        return out.toByteArray()
    }

    /**
     * テキストファイルを書き込む。
     */
    fun writeText(relPath: String, content: String): Long {
        val sftp = ensureConnected()
        val fullPath = resolveRemotePath(relPath)
        val bytes = content.toByteArray(Charsets.UTF_8)
        val input = ByteArrayInputStream(bytes)

        // 親ディレクトリを作成
        val parent = fullPath.substringBeforeLast('/', "")
        if (parent.isNotBlank()) {
            makeDirs(sftp, parent)
        }

        sftp.put(input, fullPath, ChannelSftp.OVERWRITE)
        return bytes.size.toLong()
    }

    private fun makeDirs(sftp: ChannelSftp, dirPath: String) {
        val parts = dirPath.split('/').filter { it.isNotBlank() }
        var current = if (dirPath.startsWith("/")) "" else ""
        for (part in parts) {
            current = if (current.isEmpty()) (if (dirPath.startsWith("/")) "/$part" else part) else "$current/$part"
            try {
                sftp.mkdir(current)
            } catch (e: SftpException) {
                // すでに存在していれば無視
            }
        }
    }

    fun makeDir(relPath: String) {
        val sftp = ensureConnected()
        val fullPath = resolveRemotePath(relPath)
        makeDirs(sftp, fullPath)
    }

    fun delete(relPath: String, recursive: Boolean = false) {
        val sftp = ensureConnected()
        val fullPath = resolveRemotePath(relPath)
        deletePath(sftp, fullPath, recursive)
    }

    private fun deletePath(sftp: ChannelSftp, path: String, recursive: Boolean) {
        val stat = sftp.stat(path)
        if (stat.isDir) {
            if (recursive) {
                @Suppress("UNCHECKED_CAST")
                val list = sftp.ls(path) as Vector<ChannelSftp.LsEntry>
                for (item in list) {
                    val name = item.filename
                    if (name == "." || name == "..") continue
                    deletePath(sftp, "$path/$name", true)
                }
            }
            sftp.rmdir(path)
        } else {
            sftp.rm(path)
        }
    }

    fun rename(relFrom: String, relTo: String) {
        val sftp = ensureConnected()
        val from = resolveRemotePath(relFrom)
        val to = resolveRemotePath(relTo)
        sftp.rename(from, to)
    }

    /**
     * PTY 付きの対話的シェルチャンネルを開く。
     */
    fun openShell(cols: Int = 80, rows: Int = 24): ChannelShell {
        val sess = session?.takeIf { it.isConnected }
            ?: throw IllegalStateException("セッションが接続されていません")
        val shell = sess.openChannel("shell") as ChannelShell
        shell.setPtyType("xterm-256color")
        shell.setPtySize(cols, rows, cols * 8, rows * 16)
        return shell
    }

    override fun close() {
        runCatching { sftpChannel?.disconnect() }
        runCatching { session?.disconnect() }
        sftpChannel = null
        session = null
    }
}
