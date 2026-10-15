package io.github.heapy.icu4k.text

import com.ibm.icu.text.DisplayOptions as JOptions
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class DisplayOptionsOracleTest {
    private val names = listOf("GrammaticalCase", "NounClass", "PluralCategory", "Capitalization", "NameStyle", "DisplayLength", "SubstituteHandling")
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        val cause = if (e is InvocationTargetException) e.cause!! else e
        when (cause) { is IndexOutOfBoundsException -> "bounds"; else -> cause.javaClass.simpleName }
    }
    private fun enumClass(root: Class<*>, name: String) = root.declaredClasses.single { it.simpleName == name }
    private fun state(value: Any): List<String?> = names.map { (value.javaClass.getMethod("get$it").invoke(value) as Enum<*>?)?.name }

    @Test fun enumConstantsIdentifiersAndUnmodifiableLists() {
        for (name in names) {
            val j = enumClass(JOptions::class.java, name); val k = enumClass(DisplayOptions::class.java, name)
            @Suppress("UNCHECKED_CAST") val jl = j.getField("VALUES").get(null) as MutableList<Any?>
            @Suppress("UNCHECKED_CAST") val kl = k.getField("VALUES").get(null) as MutableList<Any?>
            assertEquals(jl.map { (it as Enum<*>).name }, kl.map { (it as Enum<*>).name }, name)
            for (index in listOf(-1, 0, jl.size - 1, jl.size)) assertEquals(outcome { jl[index]?.toString() }, outcome { kl[index]?.toString() })
            assertEquals(outcome { jl.clear() }, outcome { kl.clear() })
            assertEquals(outcome { jl.add(null) }, outcome { kl.add(null) })
            assertEquals(outcome { jl[0] = null }, outcome { kl[0] = null })
            assertEquals(outcome { jl.remove(null) }, outcome { kl.remove(null) })
            assertEquals(outcome { jl.addAll(emptyList()) }, outcome { kl.addAll(emptyList()) })
            val ji = jl.listIterator(); val ki = kl.listIterator(); ji.next(); ki.next()
            assertEquals(outcome { ji.remove() }, outcome { ki.remove() })
            val jm = j.methods.singleOrNull { it.name == "getIdentifier" } ?: continue
            val km = k.getMethod("getIdentifier")
            val ids = jl.indices.map { index ->
                val id = jm.invoke(jl[index]) as String
                assertEquals(id, km.invoke(kl[index]), name)
                id
            }
            for (identifier in ids + ids.map { it.uppercase() } + listOf(null, "", " dative", "feminine ", "UNKNOWN", "locative-copulative", "\u0000")) {
                val a = j.getMethod("fromIdentifier", String::class.java).invoke(null, identifier) as Enum<*>
                val b = k.getMethod("fromIdentifier", String::class.java).invoke(null, identifier) as Enum<*>
                assertEquals(a.name, b.name, "$name/$identifier")
            }
        }
    }
    @Test fun builderDefaultsNullsCopyAndIndependentSnapshots() {
        val j = JOptions.builder(); val k = DisplayOptions.builder()
        assertEquals(List(7) { "UNDEFINED" }, state(j.build()))
        assertEquals(state(j.build()), state(k.build()))
        for (round in 0..20) {
            for (name in names) {
                val jc = enumClass(JOptions::class.java, name); val kc = enumClass(DisplayOptions::class.java, name)
                val index = round % (jc.enumConstants.size + 1)
                val a = jc.enumConstants.getOrNull(index); val b = kc.enumConstants.getOrNull(index)
                val beforeJ = j.build(); val beforeK = k.build(); val beforeState = state(beforeJ)
                assertSame(j, j.javaClass.getMethod("set$name", jc).invoke(j, a))
                assertSame(k, k.javaClass.getMethod("set$name", kc).invoke(k, b))
                assertEquals(beforeState, state(beforeJ)); assertEquals(beforeState, state(beforeK))
                assertEquals(state(j.build()), state(k.build()))
                val copiedJ = j.build().copyToBuilder(); val copiedK = k.build().copyToBuilder()
                assertEquals(state(copiedJ.build()), state(copiedK.build()))
                copiedJ.setNounClass(null); copiedK.setNounClass(null)
                assertEquals(state(copiedJ.build()), state(copiedK.build()))
                assertEquals(state(j.build()), state(k.build()))
            }
        }
        assertNotEquals(j.build(), j.build()); assertNotEquals(k.build(), k.build())
    }
}
