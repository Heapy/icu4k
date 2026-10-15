// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.MessagePattern as JPattern
import java.lang.reflect.Modifier
import kotlin.random.Random
import kotlin.test.*

class MessagePatternOracleTest {
    private fun error(block: () -> Unit): Pair<String, String?>? = try { block(); null } catch (e: RuntimeException) {
        val kind = when (e) {
            is NullPointerException -> "null"
            is IndexOutOfBoundsException -> "bounds"
            else -> e.javaClass.simpleName
        }
        kind to if (kind == "null" || kind == "bounds" && e.message?.startsWith("Index") == true) null else e.message
    }
    private fun check(source: JPattern, target: MessagePattern) {
        assertEquals(source.patternString, target.getPatternString())
        assertEquals(source.apostropheMode?.name, target.getApostropheMode()?.name)
        assertEquals(source.hasNamedArguments(), target.hasNamedArguments())
        assertEquals(source.hasNumberedArguments(), target.hasNumberedArguments())
        assertEquals(source.autoQuoteApostropheDeep(), target.autoQuoteApostropheDeep())
        assertEquals(source.countParts(), target.countParts())
        for (i in 0..<source.countParts()) {
            val s = source.getPart(i)
            val t = target.getPart(i)
            assertEquals(s.toString(), t.toString())
            assertEquals(s.type.name, target.getPartType(i).name)
            assertEquals(s.index, target.getPatternIndex(i))
            assertEquals(s.length, t.getLength())
            assertEquals(s.limit, t.getLimit())
            assertEquals(s.value, t.getValue())
            assertEquals(s.argType.name, t.getArgType().name)
            assertEquals(s.type.hasNumericValue(), t.getType().hasNumericValue())
            assertEquals(source.getLimitPartIndex(i), target.getLimitPartIndex(i))
            assertEquals(source.getNumericValue(s).toRawBits(), target.getNumericValue(t).toRawBits())
            assertEquals(source.getPluralOffset(i).toRawBits(), target.getPluralOffset(i).toRawBits())
            var ss: String? = null
            var ts: String? = null
            assertEquals(error { ss = source.getSubstring(s) }, error { ts = target.getSubstring(t) })
            assertEquals(ss, ts)
            if (ss != null) for (probe in listOf(ss!!, ss!! + "x", "")) {
                assertEquals(source.partSubstringMatches(s, probe), target.partSubstringMatches(t, probe))
            }
        }
    }
    private fun parse(source: JPattern, target: MessagePattern, kind: Int, text: String?) {
        val se = error { when (kind) { 0 -> source.parse(text); 1 -> source.parseChoiceStyle(text); 2 -> source.parsePluralStyle(text); else -> source.parseSelectStyle(text) } }
        val te = error { when (kind) { 0 -> target.parse(text); 1 -> target.parseChoiceStyle(text); 2 -> target.parsePluralStyle(text); else -> target.parseSelectStyle(text) } }
        assertEquals(se, te, "kind=$kind pattern=$text")
        check(source, target)
    }
    @Test fun publicSurface() {
        fun type(t: Class<*>): Class<*> = if (t.isArray) java.lang.reflect.Array.newInstance(type(t.componentType), 0).javaClass else when (t) {
            JPattern::class.java -> MessagePattern::class.java
            JPattern.Part::class.java -> MessagePattern.Part::class.java
            JPattern.Part.Type::class.java -> MessagePattern.Part.Type::class.java
            JPattern.ArgType::class.java -> MessagePattern.ArgType::class.java
            JPattern.ApostropheMode::class.java -> MessagePattern.ApostropheMode::class.java
            else -> t
        }
        for (sc in listOf(JPattern::class.java, JPattern.Part::class.java, JPattern.Part.Type::class.java, JPattern.ArgType::class.java, JPattern.ApostropheMode::class.java)) {
            for (m in sc.declaredMethods.filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }) {
                val tm = type(sc).getMethod(m.name, *m.parameterTypes.map(::type).toTypedArray())
                assertEquals(type(m.returnType), tm.returnType, m.toString())
            }
        }
        assertTrue(Cloneable::class.java.isAssignableFrom(MessagePattern::class.java))
        for (c in JPattern::class.java.constructors) MessagePattern::class.java.getConstructor(*c.parameterTypes.map(::type).toTypedArray())
    }
    @Test fun validInvalidAndTruncatedPatterns() {
        val patterns = listOf(
            "", "a'b", "abc'", "a''b", "'{x}'", "'{x}", "}", "{0}", "{name}", "{٠}", "{01}", "{0a}",
            "{32767}", "{32768}", "{2147483647}", "{2147483646}", "{2147483640}", "{ 1 ,number, #,##0.00}",
            "{x,date, 'hello' {world}}", "{x,date,'}", "{x,plural}", "{x,PlUrAl,offset:1 =0{none} one{'#' #} other{{x,number}}}",
            "{x,selectordinal,one{#st}two{#nd}other{#th}}", "{g,select,male{He}female{She}other{They}}",
            "{x,choice,-∞#none|0≤zero|1<some {y,select,other{nested}}}",
            "{n,plural,o{prefix accepted}}", "{n,plural,othe{prefix accepted}}", "{n,plural,others{rejected}}",
            "{x,plural,offset: 1 offset:2 other{#}}", "{x,plural,other{#} offset:2}",
            "{x,plural,=+1.1e-2{yes}other{no}}", "{x,plural,=∞{x}other{y}}", "{x,plural,=1e+{x}other{y}}",
            "{x,select,other{a''b '{z}'}}", "{x,number,foo{bar}baz}", "{x,number,foo}tail", "{x,💥,foo}",
        )
        for (mode in JPattern.ApostropheMode.entries) {
            val s = JPattern(mode)
            val t = MessagePattern(MessagePattern.ApostropheMode.valueOf(mode.name))
            for (p in patterns) for (end in 0..p.length) parse(s, t, 0, p.substring(0, end))
            for (kind in 1..3) for (p in listOf(
                "", "0#zero|1<one", "-∞#negative|+∞≤positive", "1e999#overflow", "1e-999#underflow", "-0#zero|-0.0#negative zero",
                "offset:1 =0{none}one{#}other{#}", "one{one}other{# {x,number}}", "o{x}", "other{'#'}", "=32767{x}=32768{y}=-32768{z}=-32769{w}other{#}",
                "other{x}}", "other{{x}}", "=+{x}other{y}", "=.{x}other{y}", "=1..2{x}other{y}", "=1e..2{x}other{y}", "=000..1{x}other{y}", "=+..{x}other{y}", "offset:0.5 other{#}",
            )) parse(s, t, kind, p)
        }
    }
    @Test fun randomizedSyntaxAndStateAfterFailures() {
        val random = Random(783)
        val tokens = listOf("{", "}", ",", "'", "''", "#", "|", "=", "offset:", "other", "one", "0", "01", "-1", "0.5", "1e+", "∞", "number", "plural", "choice", "select", "selectordinal", "x", " ", "\u200e", "\u0085", "😀")
        for (mode in JPattern.ApostropheMode.entries) {
            val s = JPattern(mode)
            val t = MessagePattern(MessagePattern.ApostropheMode.valueOf(mode.name))
            repeat(2200) {
                val p = buildString { repeat(random.nextInt(1, 18)) { append(tokens.random(random)) } }
                parse(s, t, random.nextInt(4), p)
            }
        }
    }
    @Test fun lengthBoundsNamesCloningAndFreeze() {
        for (p in listOf("{" + "a".repeat(65536) + "}", "{x," + "a".repeat(65536) + "}", "{x,number," + "a".repeat(65536) + "}", "{x,plural," + "a".repeat(65536) + "{x}other{y}}", "{x,plural,=" + "1".repeat(65536) + "{x}other{y}}")) parse(JPattern(), MessagePattern(), 0, p)
        for (name in listOf("", "0", "00", "2147483646", "2147483640", "2147483647", "999999999999a", "01a", "x", "😀", "a b", "a\u0085b", "ä", "a_b")) {
            assertEquals(JPattern.validateArgumentName(name), MessagePattern.validateArgumentName(name), name)
        }
        parse(JPattern(), MessagePattern(), 2, "=" + "1".repeat(2000) + "e{x}other{y}")
        val s = JPattern("{x,plural,=0.5{#}other{'#'}}")
        val t = MessagePattern(s.patternString)
        val sc = s.cloneAsThawed()
        val tc = t.cloneAsThawed()
        assertSame(s.getPart(0), sc.getPart(0))
        assertSame(t.getPart(0), tc.getPart(0))
        assertEquals(s == sc, t == tc)
        assertEquals(s.hashCode() == sc.hashCode(), t.hashCode() == tc.hashCode())
        s.freeze(); t.freeze()
        assertSame(s, s.clone()); assertSame(t, t.clone())
        assertFalse(t.cloneAsThawed().isFrozen())
        parse(s, t, 0, "new")
        assertEquals(error { s.clear() }, error { t.clear() })
        assertEquals(error { s.clearPatternAndSetApostropheMode(null) }, error { t.clearPatternAndSetApostropheMode(null) })
        sc.clear(); tc.clear()
        check(sc, tc)
        // Reflection avoids Kotlin's non-null Object.toString contract at the call site.
        assertEquals(JPattern::class.java.getMethod("toString").invoke(sc), MessagePattern::class.java.getMethod("toString").invoke(tc))
        parse(sc, tc, 0, null)
    }
}
