// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.BinaryBufferStorage
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

internal actual fun binaryFileSystem(): BinaryFileSystem? = JvmBinaryFileSystem
private object JvmBinaryFileSystem : BinaryFileSystem {
    override val pathSeparator: Char = File.pathSeparatorChar
    override val fileSeparator: String = File.separator
    override fun listFiles(path: String): List<BinaryFileEntry>? = File(path).listFiles()?.map { BinaryFileEntry(it.name, it.path, it.isDirectory) }
    override fun mapFile(path: String): BinaryBuffer? = try {
        val file = FileInputStream(path)
        try { BinaryBuffer(MappedStorage(file.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.channel.size()))) }
        finally { file.close() }
    } catch (failure: IOException) { System.err.println(failure); null }
}
private class MappedStorage(private val data: ByteBuffer) : BinaryBufferStorage {
    override val size: Int get() = data.capacity()
    override fun get(index: Int): Byte = data.get(index)
    override fun set(index: Int, value: Byte): Unit = throw UnsupportedOperationException("Read-only buffer")
    override fun copyOfRange(start: Int, end: Int): ByteArray = ByteArray(end - start).also { target ->
        data.duplicate().position(start).limit(end).get(target)
    }
}
