package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.UCaseProps as JavaCases
import com.ibm.icu.lang.UCharacter as JavaCharacter
import com.ibm.icu.text.UnicodeSet
import com.ibm.icu.impl.ICUBinary as JavaBinary
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.lang.UCharacter
import kotlin.test.*

class UCasePropsOracleTest {
    @Test fun allSimpleMappingsCaseFlagsAndFullFoldingMatchIcu() {
        val expected = JavaCases.INSTANCE
        val actual = UCaseProps.INSTANCE
        val javaOutput = StringBuilder()
        val commonOutput = StringBuilder()
        for (cp in -1..0x110000) {
            if (expected.tolower(cp) != actual.tolower(cp)) fail("Lower U+${cp.toString(16)}")
            if (expected.toupper(cp) != actual.toupper(cp)) fail("Upper U+${cp.toString(16)}")
            if (expected.totitle(cp) != actual.totitle(cp)) fail("Title U+${cp.toString(16)}")
            if (expected.getType(cp) != actual.getType(cp)) fail("Case type U+${cp.toString(16)}")
            if (expected.getTypeOrIgnorable(cp) != actual.getTypeOrIgnorable(cp)) fail("Ignorable U+${cp.toString(16)}")
            if (expected.getDotType(cp) != actual.getDotType(cp)) fail("Dot type U+${cp.toString(16)}")
            if (expected.isSoftDotted(cp) != actual.isSoftDotted(cp)) fail("Soft dotted U+${cp.toString(16)}")
            if (expected.isCaseSensitive(cp) != actual.isCaseSensitive(cp)) fail("Case sensitive U+${cp.toString(16)}")
            for (options in listOf(0, 1)) {
                if (expected.fold(cp, options) != actual.fold(cp, options)) fail("Fold U+${cp.toString(16)} option $options")
                javaOutput.setLength(0); commonOutput.setLength(0)
                val a = actual.toFullFolding(cp, commonOutput, options)
                val b = expected.toFullFolding(cp, javaOutput, options)
                if (a != b || javaOutput.toString() != commonOutput.toString()) fail("Full fold U+${cp.toString(16)} option $options")
            }
        }
    }

    @Test fun fullMappingsAndCasePropertiesMatchIcu() {
        val expected = JavaCases.INSTANCE
        val actual = UCaseProps.INSTANCE
        val a = StringBuilder()
        val b = StringBuilder()
        val properties = listOf(UProperty.LOWERCASE, UProperty.UPPERCASE, UProperty.SOFT_DOTTED,
            UProperty.CASE_SENSITIVE, UProperty.CASED, UProperty.CASE_IGNORABLE,
            UProperty.CHANGES_WHEN_LOWERCASED, UProperty.CHANGES_WHEN_UPPERCASED,
            UProperty.CHANGES_WHEN_TITLECASED, UProperty.CHANGES_WHEN_CASEMAPPED)
        for (cp in 0..0x10ffff) {
            for (mode in 0..2) {
                a.setLength(0); b.setLength(0)
                val av = when (mode) { 0 -> actual.toFullLower(cp, null, a, 1); 1 -> actual.toFullUpper(cp, null, a, 1); else -> actual.toFullTitle(cp, null, a, 1) }
                val bv = when (mode) { 0 -> expected.toFullLower(cp, null, b, 1); 1 -> expected.toFullUpper(cp, null, b, 1); else -> expected.toFullTitle(cp, null, b, 1) }
                if (av != bv || a.toString() != b.toString()) fail("Full case $mode U+${cp.toString(16)}")
            }
            for (property in properties) if (expected.hasBinaryProperty(cp, property) != actual.hasBinaryProperty(cp, property)) fail("Case property $property U+${cp.toString(16)}")
        }
    }

    @Test fun contextualMappingsMatchIcuAcrossCaseLocales() {
        val samples = listOf("I\u0307", "I\u0323\u0307", "I\u0301", "J\u0300", "\u012e\u0301", "i\u0307", "j\u0323\u0307",
            "ΟΣ", "ΟΣΑ", "ΟΣ\u0301", "ΟΣ'Α", "ΟΣ' ", "և", "İIßΣς", "\u00cc\u00cd\u0128", "a\ud801\udc00Σ")
        for (text in samples) {
            val cps = text.codePoints().toArray()
            for (i in cps.indices) for (locale in 1..6) for (mode in 0..2) {
                class Cursor {
                    var direction = 1
                    var index = i
                    fun reset(dir: Int) { direction = dir; index = if (dir < 0) i - 1 else i + 1 }
                    fun next(): Int = if (index in cps.indices) cps[index].also { index += direction } else -1
                }
                val javaCursor = Cursor()
                val commonCursor = Cursor()
                val ja = object : JavaCases.ContextIterator {
                    override fun reset(dir: Int) = javaCursor.reset(dir)
                    override fun next(): Int = javaCursor.next()
                }
                val ca = object : UCaseProps.ContextIterator {
                    override fun reset(dir: Int) = commonCursor.reset(dir)
                    override fun next(): Int = commonCursor.next()
                }
                val a = StringBuilder(); val b = StringBuilder()
                val cp = cps[i]
                val av = when (mode) { 0 -> UCaseProps.INSTANCE.toFullLower(cp, ca, a, locale); 1 -> UCaseProps.INSTANCE.toFullUpper(cp, ca, a, locale); else -> UCaseProps.INSTANCE.toFullTitle(cp, ca, a, locale) }
                val bv = when (mode) { 0 -> JavaCases.INSTANCE.toFullLower(cp, ja, b, locale); 1 -> JavaCases.INSTANCE.toFullUpper(cp, ja, b, locale); else -> JavaCases.INSTANCE.toFullTitle(cp, ja, b, locale) }
                assertEquals(bv, av, "U+${cp.toString(16)}, mode $mode, locale $locale")
                assertEquals(b.toString(), a.toString())
            }
        }
    }

    @Test fun caseClosuresAndUnfoldTableMatchIcu() {
        val javaSet = UnicodeSet()
        val commonSet = UnicodeSet()
        for (cp in 0..0x10ffff) {
            javaSet.clear(); commonSet.clear()
            JavaCases.INSTANCE.addCaseClosure(cp, javaSet)
            UCaseProps.INSTANCE.addCaseClosure(cp, { commonSet.add(it) }, { commonSet.add(it) })
            if (javaSet != commonSet) fail("Case closure U+${cp.toString(16)}")
            javaSet.clear(); commonSet.clear()
            JavaCases.INSTANCE.addSimpleCaseClosure(cp, javaSet)
            UCaseProps.INSTANCE.addSimpleCaseClosure(cp) { commonSet.add(it) }
            if (javaSet != commonSet) fail("Simple case closure U+${cp.toString(16)}")
            // Every full folding is a candidate for the reverse/unfold lookup.
            val folded = JavaCharacter.foldCase(JavaCharacter.toString(cp), true)
            if (folded.length > 1 && (JavaCases.INSTANCE.getType(cp) != 0 || folded != JavaCharacter.toString(cp))) {
                javaSet.clear(); commonSet.clear()
                assertEquals(JavaCases.INSTANCE.addStringCaseClosure(folded, javaSet), UCaseProps.INSTANCE.addStringCaseClosure(folded, { commonSet.add(it) }, { commonSet.add(it) }))
                assertEquals(javaSet, commonSet)
            }
        }
        val source = JavaBinary.getRequiredData("ucase.icu")
        val expectedBytes = ByteArray(source.remaining()).also { source.get(it) }
        assertContentEquals(expectedBytes, ICUData.getBytes("ucase.icu"))
        javaSet.clear(); commonSet.clear()
        JavaCases.INSTANCE.addPropertyStarts(javaSet)
        UCaseProps.INSTANCE.forEachPropertyStart { commonSet.add(it) }
        assertEquals(javaSet, commonSet)
    }

    @Test fun publicStringFoldingMatchesIcu() {
        val text = buildString { for (cp in 0..0x10ffff) append(JavaCharacter.toString(cp)) }
        for (options in listOf(0, 1, 7, 8, -1)) {
            val expected = JavaCharacter.foldCase(text, options)
            val actual = UCharacter.foldCase(text, options)
            if (expected != actual) {
                val first = (0 until minOf(expected.length, actual.length)).firstOrNull { expected[it] != actual[it] }
                fail("String fold option $options, lengths ${expected.length}/${actual.length}, first differing offset $first, expected unit ${first?.let { expected[it].code }}, actual unit ${first?.let { actual[it].code }}")
            }
        }
        for (defaultMapping in listOf(false, true)) {
            assertEquals(JavaCharacter.foldCase("İIßΣς", defaultMapping), UCharacter.foldCase("İIßΣς", defaultMapping))
        }
    }
}
