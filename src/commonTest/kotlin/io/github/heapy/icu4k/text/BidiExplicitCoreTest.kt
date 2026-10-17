// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UCharacter
import kotlin.test.*

class BidiExplicitCoreTest {
    @Test fun explicitLevelsAndPartialErrorsMatchSourceFingerprints() {
        val texts = listOf("abc", "אבג", "a(אב)z", "ا[12(א)]a", "a\r\nאב\na\u2029ب",
            "\u202aa\u202bא(12)\u202c\u202cz", "\u202da(אב)\u202cב", "a\u2066א\u2067b\u2069ג\u2069z",
            "\u2069a\u202cא", "a\u200c\u0300א", "\u202b".repeat(140) + "a(אב)" + "\u202c".repeat(150),
            "\u2067".repeat(140) + "a(אב)" + "\u2069".repeat(150), "a\u202b\u2067".repeat(70) + "[א12]" + "\u202c\u2069".repeat(80))
        // Captured from ICU4J 78.3 private methods using these exact raw inputs.
        // Paragraph property scanning is a separate stage, not simulated here.
        val expected = longArrayOf(
-4839598777315934896L,
-4839598777315934896L,
5052757558448181431L,
5052757558448181431L,
-2948888022727754625L,
-2948888022727754625L,
7079919755531894692L,
7079919755531894692L,
-4839598777315934896L,
-4839598777315934896L,
5052757558448181431L,
5052757558448181431L,
-2948888022727754625L,
-2948888022727754625L,
7079919755531894692L,
7079919755531894692L,
3630279612879992599L,
3630279612879992599L,
3642070332253428600L,
3642070332253428600L,
5593785948942094538L,
5593785948942094538L,
-3316582763148665286L,
-3316582763148665286L,
3630279612879992599L,
3630279612879992599L,
3642070332253428600L,
3642070332253428600L,
5593785948942094538L,
5593785948942094538L,
-3316582763148665286L,
-3316582763148665286L,
3630279612879992599L,
3630279612879992599L,
3642070332253428600L,
3642070332253428600L,
5593785948942094538L,
5593785948942094538L,
-3316582763148665286L,
-3316582763148665286L,
3630279612879992599L,
3630279612879992599L,
3642070332253428600L,
3642070332253428600L,
5593785948942094538L,
5593785948942094538L,
-3316582763148665286L,
-3316582763148665286L,
3630279612879992599L,
3630279612879992599L,
3642070332253428600L,
3642070332253428600L,
5593785948942094538L,
5593785948942094538L,
-3316582763148665286L,
-3316582763148665286L,
)
        var fixture = 0
        for (mode in 0..6) for (level in listOf(0, 1, 2, 125)) for (order in listOf(false, true)) {
            var hash = -3750763034362895579L
            fun mix(value: Int) { hash = (hash xor value.toLong()) * 1099511628211L }
            for (text in texts) for (operation in 0..4) {
                val input = object : BidiExplicitCore.Input {
                    override val text = text.toCharArray()
                    override val dirProps = ByteArray(text.length) { UCharacter.getDirection(text[it].code).toByte() }
                    override val levels = ByteArray(text.length) { i ->
                        when (operation) {
                            0, 1 -> 0
                            2 -> level or if ((i and 1) == 0) 128 else 0
                            3 -> i % 128
                            else -> i % 256 - 128
                        }.toByte()
                    }
                    override val length = text.length
                    override val reorderingMode = mode
                    override val paraCount = 1
                    override val paraLimits = intArrayOf(text.length)
                    override val paraLevels = byteArrayOf(level.toByte())
                    override val paraLevel = level.toByte()
                    override val defaultParaLevel: Byte = 0
                    override val orderParagraphsLTR = order
                    override var flags = dirProps.fold(1 shl (level and 1)) { flags, prop -> flags or (1 shl prop.toInt()) }
                    override var isolateCount = 0
                    override fun getParaLevelAt(index: Int) = paraLevel
                }
                val core = BidiExplicitCore(input)
                try {
                    val result = if (operation == 0) core.resolve() else core.check()
                    mix(0); mix(result.toInt())
                } catch (e: IndexOutOfBoundsException) {
                    mix(1)
                } catch (e: IllegalArgumentException) {
                    mix(2); mix(e.message!!.hashCode())
                }
                mix(input.flags); mix(input.isolateCount)
                for (v in input.dirProps) mix(v.toInt())
                for (v in input.levels) mix(v.toInt())
            }
            assertEquals(expected[fixture++], hash, "mode=$mode level=$level order=$order")
        }
    }
}
