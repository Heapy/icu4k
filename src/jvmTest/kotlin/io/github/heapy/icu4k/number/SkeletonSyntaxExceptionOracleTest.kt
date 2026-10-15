package io.github.heapy.icu4k.number

import com.ibm.icu.number.SkeletonSyntaxException as JException
import java.io.*
import kotlin.test.*

class SkeletonSyntaxExceptionOracleTest {
    @Test fun tokenSnapshotCausePolicyAndSerialization() {
        var calls = 0
        var text = "[first]"
        val token = object : CharSequence {
            override val length: Int get() = error("message must use toString")
            override fun get(index: Int): Char = error("message must use toString")
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = error("message must use toString")
            override fun toString(): String { calls++; return text }
        }
        for (message in listOf(null, "bad")) for (withCause in listOf(false, true)) {
            val marker = IOException("marker")
            calls = 0
            val j = if (withCause) JException(message, token, marker) else JException(message, token)
            assertEquals(1, calls)
            calls = 0
            val k = if (withCause) SkeletonSyntaxException(message, token, marker) else SkeletonSyntaxException(message, token)
            assertEquals(1, calls); assertEquals(j.message, k.message)
            text = "[changed]"; assertEquals(j.message, k.message)
            if (withCause) { assertSame(marker, j.cause); assertSame(marker, k.cause) }
            else { assertSame(j, j.initCause(marker)); assertSame(k, k.initCause(marker)) }
            val bytes = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(k) } }.toByteArray()
            val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() } as SkeletonSyntaxException
            assertEquals(k.message, restored.message); assertEquals(marker.message, restored.cause!!.message)
        }
        assertEquals(ObjectStreamClass.lookup(JException::class.java).serialVersionUID, ObjectStreamClass.lookup(SkeletonSyntaxException::class.java).serialVersionUID)
        val j = JException(null, null, null); val k = SkeletonSyntaxException(null, null, null)
        assertEquals(j.message, k.message)
        assertFailsWith<IllegalStateException> { j.initCause(IOException()) }
        assertFailsWith<IllegalStateException> { k.initCause(IOException()) }
    }
}
