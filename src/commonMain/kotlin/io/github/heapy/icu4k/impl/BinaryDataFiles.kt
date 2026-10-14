// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer

internal data class BinaryFileEntry(val name: String, val path: String, val isDirectory: Boolean)
internal interface BinaryFileSystem {
    val pathSeparator: Char
    val fileSeparator: String
    fun getFileEntry(path: String): BinaryFileEntry?
    fun listFiles(path: String): List<BinaryFileEntry>?
    fun mapFile(path: String): BinaryBuffer?
}
internal expect fun binaryFileSystem(): BinaryFileSystem?

/** Original ordered dataPath scan and single-file/package lookup. */
internal class BinaryDataFiles(dataPath: String?, private val fileSystem: BinaryFileSystem?) {
    private interface DataFile {
        fun getData(path: String): BinaryBuffer?
        fun addBaseNames(folder: String, suffix: String, names: MutableSet<String>)
    }
    private inner class SingleFile(private val itemPath: String, private val path: String) : DataFile {
        override fun getData(path: String): BinaryBuffer? = if (path == itemPath) fileSystem!!.mapFile(this.path) else null
        override fun addBaseNames(folder: String, suffix: String, names: MutableSet<String>) {
            // Preserve upstream's leading-slash test, including its root-folder behavior.
            if (itemPath.length > folder.length + suffix.length && itemPath.startsWith(folder) &&
                itemPath.endsWith(suffix) && itemPath[folder.length] == '/' && itemPath.indexOf('/', folder.length + 1) < 0) {
                names.add(itemPath.substring(folder.length + 1, itemPath.length - suffix.length))
            }
        }
    }
    private class PackageFile(private val pkg: ICUDataPackage) : DataFile {
        override fun getData(path: String): BinaryBuffer? = pkg.getData(path)
        override fun addBaseNames(folder: String, suffix: String, names: MutableSet<String>) = pkg.addBaseNamesInFolder(folder, suffix, names)
    }
    private val files = ArrayList<DataFile>()
    init {
        if (dataPath != null && fileSystem != null) {
            var start = 0
            while (start < dataPath.length) {
                val separator = dataPath.indexOf(fileSystem.pathSeparator, start)
                val limit = if (separator >= 0) separator else dataPath.length
                var path = dataPath.substring(start, limit).trim { it <= '\u0020' }
                if (path.endsWith(fileSystem.fileSeparator)) path = path.dropLast(fileSystem.fileSeparator.length)
                if (path.isNotEmpty()) scanFolder(path, "")
                if (separator < 0) break
                start = separator + 1
            }
        }
    }
    private fun scanFolder(path: String, prefix: String) {
        val entries = fileSystem!!.listFiles(path) ?: return
        for (entry in entries) {
            if (entry.name.endsWith(".txt")) continue
            val item = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
            when {
                entry.isDirectory -> scanFolder(entry.path, item)
                entry.name.endsWith(".dat") -> fileSystem.mapFile(entry.path)?.let { ICUDataPackage.open(it) }?.let { files.add(PackageFile(it)) }
                else -> files.add(SingleFile(item, entry.path))
            }
        }
    }
    fun getData(itemPath: String): BinaryBuffer? {
        for (file in files) file.getData(itemPath)?.let { return it }
        return null
    }
    fun addBaseNamesInFolder(folder: String, suffix: String, names: MutableSet<String>) {
        for (file in files) file.addBaseNames(folder, suffix, names)
    }
}
