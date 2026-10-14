// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer

/** Validated ICU CmnD package, borrowing the supplied buffer and its storage. */
class ICUDataPackage private constructor(private val bytes: BinaryBuffer) {
    fun getData(itemPath: CharSequence): BinaryBuffer? = DatPackageReader.getData(bytes, itemPath)
    fun addBaseNamesInFolder(folder: String, suffix: String, names: MutableSet<String>) =
        DatPackageReader.addBaseNamesInFolder(bytes, folder, suffix, names)

    /** Adapts package item paths to the binary bundle loader's resource namespace. */
    fun asResourceLoader(baseName: String = ICUData.ICU_BASE_NAME): ICUResourceLoader = object : ICUResourceLoader {
        private val prefix = if (baseName.endsWith('/')) baseName else "$baseName/"
        override fun getData(fullName: String): BinaryBuffer? =
            if (fullName.startsWith(prefix)) this@ICUDataPackage.getData(fullName.substring(prefix.length)) else null
        override fun visitResources(directory: String, visitor: (String) -> Unit) {
            if (!directory.startsWith(prefix)) return
            val names = linkedSetOf<String>()
            addBaseNamesInFolder(directory.substring(prefix.length).removeSuffix("/"), "", names)
            names.forEach(visitor)
        }
    }
    companion object {
        /** Rejects unauthenticated packages with null; malformed tables may throw bounds errors. */
        fun open(bytes: BinaryBuffer): ICUDataPackage? = if (DatPackageReader.validate(bytes)) ICUDataPackage(bytes) else null
    }
}
