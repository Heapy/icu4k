// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.BinaryBufferStorage
import io.github.heapy.icu4k.util.ICUUncheckedIOException

internal actual fun binaryFileSystem(): BinaryFileSystem? = NodeBinaryFileSystem.instance
private class NodeBinaryFileSystem(private val fs: dynamic, private val path: dynamic) : BinaryFileSystem {
    override val pathSeparator: Char = (path.delimiter as String)[0]
    override val fileSeparator: String = path.sep as String
    override fun listFiles(path: String): List<BinaryFileEntry>? = try {
        val entries: dynamic = fs.readdirSync(path)
        List(entries.length as Int) { index ->
            val name = entries[index] as String
            val fullName = this.path.join(path, name) as String
            val directory = try { fs.statSync(fullName).isDirectory() as Boolean } catch (_: Throwable) { false }
            BinaryFileEntry(name, fullName, directory)
        }
    } catch (_: Throwable) { null }
    override fun mapFile(path: String): BinaryBuffer? {
        var descriptor: Int? = null
        try {
            val fd = fs.openSync(path, "r") as Int
            descriptor = fd
            val size = fs.fstatSync(fd).size as Double
            require(size >= 0 && size <= Int.MAX_VALUE.toDouble()) { "File is too large to map" }
            val storage = NodeFileStorage(fs, fd, size.toInt())
            descriptor = null // Storage/finalizer owns the descriptor.
            return BinaryBuffer(storage)
        } catch (failure: Throwable) {
            if (failure is IllegalArgumentException) throw failure
            js("console.error")(failure.toString())
            return null
        } finally { if (descriptor != null) try { fs.closeSync(descriptor) } catch (_: Throwable) {} }
    }
    companion object {
        val instance: BinaryFileSystem? = if (js("typeof process !== 'undefined' && process.versions && process.versions.node && typeof require === 'function'") as Boolean)
            NodeBinaryFileSystem(js("require('node:fs')"), js("require('node:path')")) else null
    }
}
/** Node has no built-in mmap; retained descriptors provide live, inode-stable reads. */
private class NodeFileStorage(private val fs: dynamic, private val descriptor: Int, override val size: Int) : BinaryBufferStorage {
    private val single: dynamic = js("new Uint8Array(1)")
    init {
        val info: dynamic = js("({})")
        info.fs = fs; info.fd = descriptor
        finalizers?.register(this, info)
    }
    override fun get(index: Int): Byte {
        if ((fs.readSync(descriptor, single, 0, 1, index) as Int) != 1) throw ICUUncheckedIOException("Mapped file is truncated")
        return (single[0] as Int).toByte()
    }
    override fun set(index: Int, value: Byte): Unit = throw UnsupportedOperationException("Read-only buffer")
    override fun copyOfRange(start: Int, end: Int): ByteArray {
        val length = end - start
        val target: dynamic = js("new Uint8Array(length)")
        var count = 0
        while (count < length) {
            val read = fs.readSync(descriptor, target, count, length - count, start + count) as Int
            if (read == 0) throw ICUUncheckedIOException("Mapped file is truncated")
            count += read
        }
        return ByteArray(length) { (target[it] as Int).toByte() }
    }
    companion object {
        private val finalizers: dynamic = js("typeof FinalizationRegistry !== 'undefined' ? new FinalizationRegistry(function(info) { try { info.fs.closeSync(info.fd); } catch (_) {} }) : null")
    }
}
