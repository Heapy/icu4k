package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

class BinaryDataFilesTest {
    private class Files : BinaryFileSystem {
        override val pathSeparator = ':'
        override val fileSeparator = "/"
        val directories = linkedMapOf<String, List<BinaryFileEntry>>()
        val contents = linkedMapOf<String, ByteArray>()
        val scans = mutableListOf<String>()
        val maps = mutableListOf<String>()
        override fun listFiles(path: String): List<BinaryFileEntry>? { scans.add(path); return directories[path] }
        override fun mapFile(path: String): BinaryBuffer? { maps.add(path); return contents[path]?.let { BinaryBuffer(it, isReadOnly = true) } }
        fun folder(path: String, vararg names: String) {
            directories[path] = names.map { BinaryFileEntry(it.removeSuffix("/"), "$path/${it.removeSuffix("/")}", it.endsWith('/')) }
        }
    }
    @Test fun scansInOrderAndPreservesPathAndFileFiltering() {
        val fs = Files()
        fs.folder("/first", "ignored.txt/", "single.res", "coll/", "bundle.dat", "bad.dat")
        fs.folder("/first/coll", "en.res", "nested/")
        fs.folder("/first/coll/nested", "de.res")
        fs.folder("/second//", "single.res")
        fs.contents["/first/single.res"] = byteArrayOf(10)
        fs.contents["/first/coll/en.res"] = byteArrayOf(20)
        fs.contents["/first/coll/nested/de.res"] = byteArrayOf(21)
        fs.contents["/second///single.res"] = byteArrayOf(30)
        fs.contents["/first/bundle.dat"] = DatPackageFixture.write(mapOf("single.res" to ByteArray(16) { 40 }, "coll/fr.res" to ByteArray(16) { 41 }, "root.res" to ByteArray(16)))
        fs.contents["/first/bad.dat"] = ByteArray(32)
        val files = BinaryDataFiles(" /first/ : : /second/// :\u00a0/space\u00a0", fs)
        assertEquals(listOf("/first", "/first/coll", "/first/coll/nested", "/second//", "\u00a0/space\u00a0"), fs.scans)
        assertEquals(listOf("/first/bundle.dat", "/first/bad.dat"), fs.maps)
        assertEquals(10, files.getData("single.res")!!.readUnsignedByte())
        assertEquals(20, files.getData("coll/en.res")!!.readUnsignedByte())
        assertEquals(41, files.getData("coll/fr.res")!!.readUnsignedByte())
        assertNull(files.getData("ignored.txt/x.res"))
        val names = linkedSetOf<String>(); files.addBaseNamesInFolder("coll", ".res", names)
        assertEquals(setOf("en", "fr"), names)
        val roots = linkedSetOf<String>(); files.addBaseNamesInFolder("", ".res", roots)
        // Single root files fail ICU4J's slash test. Package entries still contribute.
        assertEquals(setOf("single", "root"), roots)
        fs.contents.remove("/first/single.res")
        assertEquals(40, files.getData("single.res")!!.readUnsignedByte())
        assertEquals(listOf("/first", "/first/coll", "/first/coll/nested", "/second//", "\u00a0/space\u00a0"), fs.scans)
    }
    @Test fun packagesAreRetainedButSingleFilesAreMappedPerLookup() {
        val fs = Files(); fs.folder("/data", "package.dat", "raw.bin")
        val bytes = DatPackageFixture.write(mapOf("a.bin" to ByteArray(16) { 1 }))
        fs.contents["/data/package.dat"] = bytes
        fs.contents["/data/raw.bin"] = byteArrayOf(2)
        val files = BinaryDataFiles("/data", fs)
        val first = files.getData("a.bin")!!
        bytes[first.arrayOffset] = 9
        assertEquals(9, files.getData("a.bin")!!.readUnsignedByte())
        assertEquals(9, first.readUnsignedByte())
        files.getData("raw.bin"); fs.contents["/data/raw.bin"] = byteArrayOf(3)
        assertEquals(3, files.getData("raw.bin")!!.readUnsignedByte())
        assertEquals(listOf("/data/package.dat", "/data/raw.bin", "/data/raw.bin"), fs.maps)
        assertFailsWith<UnsupportedOperationException> { first.writeByte(0) }
        assertNull(BinaryDataFiles(null, fs).getData("a.bin"))
        assertNull(BinaryDataFiles("/", fs).getData("a.bin"))
        assertNull(BinaryDataFiles("/data", null).getData("a.bin"))
    }
}
