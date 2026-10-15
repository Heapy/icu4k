package io.github.heapy.icu4k.util

import com.ibm.icu.util.ULocale as JLocale
import java.io.*
import kotlin.test.*

class ULocaleSerializationOracleTest {
    private fun roundTrip(value: Any): Any = ByteArrayOutputStream().let { bytes ->
        ObjectOutputStream(bytes).use { it.writeObject(value) }
        ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
    }
    @Test fun streamContainsOnlyLocaleIdentityAndRestoresTransientCaches() {
        val jd = ObjectStreamClass.lookup(JLocale::class.java); val kd = ObjectStreamClass.lookup(ULocale::class.java)
        assertEquals(jd.serialVersionUID, kd.serialVersionUID)
        assertEquals(jd.fields.map { it.name to it.typeString }, kd.fields.map { it.name to it.typeString })
        for (id in listOf("", "en_US", "sr_Cyrl_RS@calendar=gregorian;currency=eur", "ja_JP_JP", "zh_Hant_TW@numbers=hanidec", "en_US@attribute=foo-bar;x=test")) for (initialize in listOf(false, true)) {
            val j = JLocale(id); val k = ULocale(id)
            if (initialize) { j.toLanguageTag(); j.toLocale(); k.toLanguageTag(); k.toLocale() }
            val jr = roundTrip(j) as JLocale; val kr = roundTrip(k) as ULocale
            assertEquals(k, kr); assertEquals(jr.name, kr.getName())
            assertEquals(jr.toLanguageTag(), kr.toLanguageTag())
            assertEquals(jr.toLocale(), kr.toLocale())
            assertEquals(jr.language, kr.getLanguage()); assertEquals(jr.script, kr.getScript())
            assertEquals(jr.country, kr.getCountry()); assertEquals(jr.variant, kr.getVariant())
            assertSame(kr, kr.clone())
        }
    }
}
