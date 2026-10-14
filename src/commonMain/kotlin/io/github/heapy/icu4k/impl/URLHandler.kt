// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2005-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Portable file/archive traversal, with JVM URL/protocol lookup at the host boundary. */
abstract class URLHandler {
    fun interface URLVisitor { fun visit(str: String) }
    fun guide(visitor: URLVisitor, recurse: Boolean) = guide(visitor, recurse, true)
    abstract fun guide(visitor: URLVisitor, recurse: Boolean, strip: Boolean)

    companion object {
        const val PROPNAME = "urlhandler.props"
        internal val DEBUG = ICUDebug.enabled("URLHandler")

        /** Visits a host filesystem path; null means it does not exist or no filesystem is present. */
        fun forFile(path: String): URLHandler? {
            val fs = binaryFileSystem() ?: return null
            val entry = fs.getFileEntry(path) ?: return null
            return FileHandler(HostURLFile(entry.name, path, fs))
        }
        /** Archive names are visited in the order supplied by a fresh iterator on each guide call. */
        fun forArchive(prefix: String, entries: () -> Iterator<ArchiveEntry>): URLHandler = ArchiveHandler(prefix, entries)
    }
    data class ArchiveEntry(val name: String, val isDirectory: Boolean)
}
internal interface URLFile {
    val name: String
    val isDirectory: Boolean
    fun children(): List<URLFile>?
}
private class HostURLFile(override val name: String, private val path: String, private val fs: BinaryFileSystem) : URLFile {
    override val isDirectory: Boolean get() = fs.getFileEntry(path)?.isDirectory == true
    override fun children(): List<URLFile>? = fs.listFiles(path)?.map { HostURLFile(it.name, it.path, fs) }
}
internal class FileHandler(private val file: URLFile) : URLHandler() {
    override fun guide(visitor: URLVisitor, recurse: Boolean, strip: Boolean) {
        if (file.isDirectory) process(visitor, recurse, strip, "/", file.children()) else visitor.visit(file.name)
    }
    private fun process(visitor: URLVisitor, recurse: Boolean, strip: Boolean, path: String, files: List<URLFile>?) {
        if (files == null) return
        for (file in files) {
            if (file.isDirectory) {
                if (recurse) process(visitor, recurse, strip, path + file.name + '/', file.children())
            } else visitor.visit(if (strip) file.name else path + file.name)
        }
    }
}
private class ArchiveHandler(private val prefix: String, private val entries: () -> Iterator<URLHandler.ArchiveEntry>) : URLHandler() {
    override fun guide(visitor: URLVisitor, recurse: Boolean, strip: Boolean) {
        try {
            val iterator = entries()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.isDirectory || !entry.name.startsWith(prefix)) continue
                var name = entry.name.substring(prefix.length)
                val slash = name.lastIndexOf('/')
                if (slash > 0 && !recurse) continue
                if (strip && slash != -1) name = name.substring(slash + 1)
                visitor.visit(name)
            }
        } catch (failure: Exception) { if (DEBUG) urlHandlerError("icurb jar error: $failure") }
    }
}
internal expect fun urlHandlerError(message: Any?)
