package com.ttech.pocketdev.ssh

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.SftpException
import com.ttech.pocketdev.ConnectionTarget
import com.ttech.pocketdev.bridge.BridgeException
import com.ttech.pocketdev.bridge.FileBackend
import com.ttech.pocketdev.bridge.FileEntry
import com.ttech.pocketdev.bridge.PtyBackend
import com.ttech.pocketdev.bridge.PtySession
import java.io.InputStream
import java.io.OutputStream

/**
 * SSH の接続先。ファイルは SFTP、ターミナルは SSH のシェルで扱う。
 * JSch の SFTP のチャンネルは同時に使えないので、ファイルの操作は1つずつ順に行う。
 */
class SshBackend(private val client: SshClient, target: ConnectionTarget) : FileBackend, PtyBackend {

    override val rootLabel: String = client.resolveRemotePath("")
    override val hostLabel: String = "${target.sshUser}@${target.sshHost}"

    @Synchronized
    override fun list(path: String): List<FileEntry> = sftp {
        client.listDir(path).map { FileEntry(it.name, it.path, it.isDir, it.size, it.mtime) }
    }

    @Synchronized
    override fun read(path: String, maxBytes: Long): ByteArray = sftp {
        val size = client.fileSize(path)
        if (size > maxBytes) throw BridgeException("大きすぎて開けません (%,d バイト)".format(size))
        client.readBytes(path)
    }

    @Synchronized
    override fun write(path: String, data: ByteArray) {
        sftp { client.writeBytes(path, data) }
    }

    @Synchronized
    override fun makeDir(path: String) = sftp { client.makeDir(path) }

    @Synchronized
    override fun delete(path: String, recursive: Boolean) = sftp { client.delete(path, recursive) }

    @Synchronized
    override fun rename(from: String, to: String) = sftp { client.rename(from, to) }

    override fun open(cols: Int, rows: Int): PtySession {
        val shell = client.openShell(cols, rows)
        val output = shell.inputStream
        val input = shell.outputStream
        shell.connect(10_000)
        return ShellSession(shell, output, input)
    }

    /** SFTP のエラー（英語の短い文）を、利用者に見せる文に直す */
    private fun <T> sftp(block: () -> T): T = try {
        block()
    } catch (e: SftpException) {
        val message = when (e.id) {
            2 -> "見つかりません" // SSH_FX_NO_SUCH_FILE
            3 -> "権限がありません" // SSH_FX_PERMISSION_DENIED
            else -> e.message ?: "SFTP のエラー（${e.id}）"
        }
        throw BridgeException(message)
    }

    private class ShellSession(
        private val shell: ChannelShell,
        override val output: InputStream,
        private val input: OutputStream,
    ) : PtySession {
        override fun write(data: ByteArray) {
            input.write(data)
            input.flush()
        }

        override fun resize(cols: Int, rows: Int) = shell.setPtySize(cols, rows, cols * 8, rows * 16)

        override fun close() {
            runCatching { input.close() }
            runCatching { shell.disconnect() }
        }
    }
}
