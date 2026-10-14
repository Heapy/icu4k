package io.github.heapy.icu4k.impl

import kotlin.test.*

class URLHandlerTest {
    private class Node(override val name: String, private val files: List<URLFile>? = null, override val isDirectory: Boolean = files != null) : URLFile {
        override fun children(): List<URLFile>? = files
    }
    @Test fun fileTraversalKeepsOrderPathsAndNullDirectoryLists() {
        val handler = FileHandler(Node("root", listOf(Node("a"), Node("nested", listOf(Node("b"))), Node("unreadable", isDirectory = true))))
        fun names(recurse: Boolean, strip: Boolean): List<String> = mutableListOf<String>().also { result -> handler.guide(URLHandler.URLVisitor(result::add), recurse, strip) }
        assertEquals(listOf("a"), names(false, true))
        assertEquals(listOf("/a"), names(false, false))
        assertEquals(listOf("a", "b"), names(true, true))
        assertEquals(listOf("/a", "/nested/b"), names(true, false))
        assertFailsWith<IllegalStateException> { handler.guide(URLHandler.URLVisitor { error("visitor") }, true) }
        val single = mutableListOf<String>()
        FileHandler(Node("file")).guide(URLHandler.URLVisitor(single::add), true, false)
        assertEquals(listOf("file"), single)
    }
    @Test fun archivePrefixAndSlashRulesPreserveExceptionPolicy() {
        val entries = listOf(URLHandler.ArchiveEntry("base/", true), URLHandler.ArchiveEntry("base/a", false),
            URLHandler.ArchiveEntry("base/nested/b", false), URLHandler.ArchiveEntry("baseball", false), URLHandler.ArchiveEntry("other", false))
        fun names(prefix: String, recurse: Boolean, strip: Boolean): List<String> = mutableListOf<String>().also { result ->
            URLHandler.forArchive(prefix) { entries.iterator() }.guide(URLHandler.URLVisitor(result::add), recurse, strip)
        }
        assertEquals(listOf("a"), names("base/", false, true))
        assertEquals(listOf("a", "b"), names("base/", true, true))
        assertEquals(listOf("a", "nested/b"), names("base/", true, false))
        assertEquals(listOf("/a", "ball"), names("base", false, false))
        assertEquals(listOf("a", "ball"), names("base", false, true))
        val seen = mutableListOf<String>()
        URLHandler.forArchive("base/") { entries.iterator() }.guide(URLHandler.URLVisitor { seen.add(it); error("stop") }, true)
        assertEquals(listOf("a"), seen)
        // Exceptions are swallowed; Errors propagate as they do in the Java archive guide.
        assertFailsWith<AssertionError> { URLHandler.forArchive("base/") { throw AssertionError("error") }.guide(URLHandler.URLVisitor {}, true) }
    }
}
