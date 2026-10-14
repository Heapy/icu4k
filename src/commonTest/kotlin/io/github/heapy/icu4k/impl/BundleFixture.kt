package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer

/** Small real ResB writer used by Common and differential bundle tests. */
internal object BundleFixture {
    data class Alias(val path: String)
    fun write(table: Map<String, Any>, noFallback: Boolean = false): ByteArray {
        val keys = linkedSetOf<String>()
        fun collect(node: Any) {
            when (node) {
                is Map<*, *> -> for ((key, value) in node) { keys.add(key as String); collect(value!!) }
                is List<*> -> node.forEach { collect(it!!) }
            }
        }
        collect(table)
        val body = BinaryBuffer(ByteArray(16384))
        body.position = 36
        val offsets = keys.associateWith { key ->
            body.position.also { for (c in key) body.writeByte(c.code); body.writeByte(0) }
        }
        fun align() { while (body.position % 4 != 0) body.writeByte(0) }
        align()
        val keysTop = body.position / 4
        fun emit(node: Any): Int {
            align()
            return when (node) {
                is Int -> 0x70000000 or (node and 0x0fffffff)
                is String, is Alias -> {
                    val type = if (node is Alias) 3 else 0
                    val text = if (node is Alias) node.path else node as String
                    val offset = body.position / 4
                    body.writeInt(text.length); for (c in text) body.writeShort(c.code)
                    (type shl 28) or offset
                }
                is ByteArray -> {
                    val offset = body.position / 4
                    body.writeInt(node.size); for (b in node) body.writeByte(b.toInt())
                    (1 shl 28) or offset
                }
                is IntArray -> {
                    val offset = body.position / 4
                    body.writeInt(node.size); for (i in node) body.writeInt(i)
                    (14 shl 28) or offset
                }
                is List<*> -> {
                    val handles = node.map { emit(it!!) }; align()
                    val offset = body.position / 4
                    body.writeInt(handles.size); handles.forEach { body.writeInt(it) }
                    (8 shl 28) or offset
                }
                is Map<*, *> -> {
                    val entries = node.entries.sortedBy { it.key as String }
                    val handles = entries.map { emit(it.value!!) }; align()
                    val offset = body.position / 4
                    body.writeShort(entries.size)
                    for (entry in entries) body.writeShort(offsets[entry.key]!!)
                    align(); handles.forEach { body.writeInt(it) }
                    (2 shl 28) or offset
                }
                else -> error("Unsupported fixture node")
            }
        }
        val root = emit(table); align()
        val top = body.position / 4
        body.position = 0; body.writeInt(root)
        for (value in intArrayOf(8, keysTop, top, top, 100, if (noFallback) 1 else 0, keysTop, 0)) body.writeInt(value)
        return ICUBinary.writeHeader(0x52657342, 0x03000000, 0x11223344) + body.bytes.copyOf(top * 4)
    }
    fun data(): Map<String, ByteArray> = mapOf(
        "root" to write(mapOf("target" to "root", "inherit" to "inherited", "nested" to mapOf("x" to "root-x", "inherited" to "deep"),
            "dynamic" to Alias("/LOCALE/target"), "cycleA" to Alias("root/cycleB"), "cycleB" to Alias("root/cycleA"),
            "strings" to listOf("a", "b"), "mixed" to listOf("a", 42), "integer" to -1, "vector" to intArrayOf(-1, 42),
            "binary" to byteArrayOf(1, 2, 3), "marker" to "∅∅∅")),
        "en" to write(mapOf("target" to "en", "nested" to mapOf("x" to "en-x"))),
        "en_US" to write(mapOf("target" to "US", "aliasGroup" to Alias("en/nested"), "inherit" to Alias("root"),
            "absolute" to Alias("/ICUDATA-brkitr/root/dictionaries/Mymr"))),
        "en_GB" to write(mapOf("%%Parent" to "en_US", "target" to "GB")),
        "fr" to write(mapOf("target" to "fr"), noFallback = true),
        "es_419" to write(mapOf("target" to "latin-america"))
    )
    fun loader(data: Map<String, ByteArray>): ICUResourceLoader = ICUResourceLoader { name ->
        data[name.substringAfterLast('/').removeSuffix(".res")]?.let { BinaryBuffer(it.copyOf()) }
    }
}
