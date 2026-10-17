package io.github.heapy.icu4k.text

import kotlin.test.*

class LegacyIDNATest {
    private fun decode(hex: String): String = CharArray(hex.length/4){hex.substring(it*4,it*4+4).toInt(16).toChar()}.concatToString()
    private fun result(block:()->Any?): String = try {"ok:${block()}"}catch(e:Throwable) {
        when(e) {
            is StringPrepParseException->"error:${e.getError()}:$e"
            is IndexOutOfBoundsException->"bounds"
            else->throw e
        }
    }
    @Test fun independentProfileAndIdna2003Goldens() {
        assertEquals(1936,legacyIdnaGoldenRows.size)
        for(row in legacyIdnaGoldenRows) {
            val fields=row.split('|');val op=fields[1].toInt();val options=fields[2].toInt();val input=decode(fields[3])
            val actual=result {
                if(fields[0]=="S")StringPrep.getInstance(op).prepare(input,options)
                else when(op) {
                    0->IDNA.convertToASCII(input,options)
                    1->IDNA.convertToUnicode(input,options)
                    2->IDNA.convertIDNToASCII(input,options)
                    else->IDNA.convertIDNToUnicode(input,options)
                }
            }
            assertEquals(decode(fields[4]),actual,row)
        }
    }
    @Test fun profileCacheInputOffsetsAndLegacyComparison() {
        for(profile in 0..13)assertSame(StringPrep.getInstance(profile),StringPrep.getInstance(profile))
        assertNotSame(StringPrep.getInstance(0),StringPrep.getInstance(3))
        for(profile in listOf(-1,14))assertEquals("Bad profile type",assertFailsWith<IllegalArgumentException>{StringPrep.getInstance(profile)}.message)
        val input=UCharacterIterator.getInstance("PREFIXStraße");input.setIndex(6)
        assertEquals("strasse",StringPrep.getInstance(0).prepare(input,0).toString());assertEquals(12,input.getIndex())
        val ascii=UCharacterIterator.getInstance("EXAMPLE");ascii.setIndex(3)
        assertEquals("EXAMPLE",IDNA.convertToASCII(ascii,0).toString());assertEquals(0,ascii.getIndex())
        assertEquals(0,IDNA.compare("a","ab",0)) // ICU4J comparison stops at the first operand's length.
        assertFailsWith<IndexOutOfBoundsException>{IDNA.compare("ab","a",0)}
        assertEquals("One of the source buffers is null",assertFailsWith<IllegalArgumentException>{IDNA.compare(null as String?,"a",0)}.message)
        val text=StringBuilder("bücher.de")
        assertEquals("xn--bcher-kva.de",IDNA.convertIDNToASCII(text,0).toString())
        assertEquals("bücher.de",text.toString())
    }
}
