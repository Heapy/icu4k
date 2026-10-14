// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.BinaryBufferStorage
import kotlinx.cinterop.*
import kotlin.native.ref.createCleaner
import platform.posix.*

internal actual fun binaryFileSystem(): BinaryFileSystem? = PosixBinaryFileSystem
private object PosixBinaryFileSystem : BinaryFileSystem {
    override val pathSeparator: Char = ':'
    override val fileSeparator: String = "/"
    override fun getFileEntry(path: String): BinaryFileEntry? {
        if ('\u0000' in path) return null
        return memScoped {
            val info = alloc<stat>()
            if (stat(path, info.ptr) != 0) null else BinaryFileEntry(path.trimEnd('/').substringAfterLast('/'), path,
                (info.st_mode.toInt() and S_IFMT.toInt()) == S_IFDIR.toInt())
        }
    }
    override fun listFiles(path: String): List<BinaryFileEntry>? {
        if ('\u0000' in path) return null
        val directory = opendir(path) ?: return null
        try {
            val result = ArrayList<BinaryFileEntry>()
            while (true) {
                val entry = readdir(directory) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name == "." || name == "..") continue
                val fullName = "$path/$name"
                val isDirectory = memScoped {
                    val info = alloc<stat>()
                    stat(fullName, info.ptr) == 0 && (info.st_mode.toInt() and S_IFMT.toInt()) == S_IFDIR.toInt()
                }
                result.add(BinaryFileEntry(name, fullName, isDirectory))
            }
            return result
        } finally { closedir(directory) }
    }
    override fun mapFile(path: String): BinaryBuffer? {
        if ('\u0000' in path) return null
        val descriptor = open(path, O_RDONLY)
        if (descriptor < 0) { reportError(path); return null }
        try {
            val size = memScoped {
                val info = alloc<stat>()
                if (fstat(descriptor, info.ptr) != 0) { reportError(path); return null }
                info.st_size
            }
            require(size >= 0 && size <= Int.MAX_VALUE) { "File is too large to map" }
            if (size == 0L) return BinaryBuffer(ByteArray(0), isReadOnly = true)
            val pointer = mmap(null, size.convert(), PROT_READ, MAP_SHARED, descriptor, 0)
            if (pointer == MAP_FAILED || pointer == null) { reportError(path); return null }
            return BinaryBuffer(PosixMappedStorage(pointer.reinterpret(), size.toInt()))
        } finally { close(descriptor) }
    }
    private fun reportError(path: String) {
        val message = strerror(errno)?.toKString() ?: "mapping failed"
        fprintf(stderr, "%s\n", "$path: $message")
    }
}
private class PosixMappedStorage(private val pointer: CPointer<ByteVar>, override val size: Int) : BinaryBufferStorage {
    private data class Region(val pointer: CPointer<ByteVar>, val size: Int)
    @Suppress("unused")
    private val cleaner = createCleaner(Region(pointer, size)) { region -> munmap(region.pointer, region.size.convert()) }
    override fun get(index: Int): Byte = pointer[index]
    override fun set(index: Int, value: Byte): Unit = throw UnsupportedOperationException("Read-only buffer")
    override fun copyOfRange(start: Int, end: Int): ByteArray = ByteArray(end - start).also { result ->
        if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), pointer + start, result.size.convert()) }
    }
}
