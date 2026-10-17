package io.github.heapy.icu4k.text

import kotlin.test.*

class IDNATest {
    private fun decode(hex: String): String = CharArray(hex.length/4){hex.substring(it*4,it*4+4).toInt(16).toChar()}.concatToString()
    @Test fun independentIcu4jGoldens() {
        assertEquals(1080,idnaGoldenRows.size)
        for(row in idnaGoldenRows) {
            val fields=row.split('|')
            val processor=IDNA.getUTS46Instance(fields[0].toInt());val op=fields[1].toInt()
            val input=decode(fields[2]);val dest=StringBuilder("old contents");val info=IDNA.Info()
            info.getErrors().add(IDNA.Error.BIDI)
            val result=when(op) {
                0->processor.labelToASCII(input,dest,info)
                1->processor.labelToUnicode(input,dest,info)
                2->processor.nameToASCII(input,dest,info)
                else->processor.nameToUnicode(input,dest,info)
            }
            assertSame(dest,result,row)
            assertEquals(decode(fields[3]),dest.toString(),row)
            assertEquals(fields[4],info.getErrors().joinToString(","){it.name},row)
            assertEquals(fields[4].isNotEmpty(),info.hasErrors(),row)
            assertEquals(fields[5].toBooleanStrict(),info.isTransitionalDifferent(),row)
        }
    }
    @Test fun errorsAreLiveOrderedAndResetForEachOperation() {
        val info=IDNA.Info();val errors=info.getErrors()
        for(error in IDNA.Error.entries.reversed())assertTrue(errors.add(error))
        assertEquals(IDNA.Error.entries.toList(),errors.toList())
        assertFalse(errors.add(IDNA.Error.BIDI))
        val iterator=errors.iterator()
        assertFailsWith<IllegalStateException>{iterator.remove()}
        assertEquals(IDNA.Error.EMPTY_LABEL,iterator.next())
        errors.clear();errors.add(IDNA.Error.BIDI);iterator.remove()
        assertEquals(setOf(IDNA.Error.BIDI),errors)
        assertEquals(IDNA.Error.LABEL_TOO_LONG,iterator.next())
        iterator.remove();assertEquals(setOf(IDNA.Error.BIDI),errors)
        val processor=IDNA.getUTS46Instance(IDNA.DEFAULT)
        val dest=StringBuilder("before")
        assertFailsWith<IllegalArgumentException>{processor.nameToASCII(dest,dest,info)}
        assertEquals("before",dest.toString());assertEquals(setOf(IDNA.Error.BIDI),errors)
        processor.nameToASCII("faß.de",dest,info)
        assertSame(errors,info.getErrors());assertTrue(errors.isEmpty())
        assertTrue(info.isTransitionalDifferent());assertEquals("xn--fa-hia.de",dest.toString())
        processor.nameToUnicode("EXAMPLE.COM",dest,info)
        assertFalse(info.isTransitionalDifferent());assertEquals("example.com",dest.toString())
        errors.clear();assertFailsWith<NoSuchElementException>{errors.iterator().next()}
    }
}
