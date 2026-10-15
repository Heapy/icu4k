package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class NumberSkeletonCoreTest {
    @Test fun canonicalizationAndFormatting() {
        val macros = NumberSkeletonCore.parseSkeleton("%x100 .00/@## group-off sign-always")
        assertEquals("percent .00/@## group-off sign-always scale/100", NumberSkeletonCore.generate(macros))
        macros.loc = ULocale.ENGLISH
        val output = FormattedStringBuilder()
        NumberFormatterImpl(macros).format(DecimalQuantity_DualStorageBCD(0.12345), output)
        assertEquals("+12.30%", output.toString())
        assertEquals("scientific/*eee/sign-except-zero", NumberSkeletonCore.generate(NumberSkeletonCore.parseSkeleton("E+?000")))
        assertEquals("unit/meter-per-second", NumberSkeletonCore.generate(NumberSkeletonCore.parseSkeleton("measure-unit/length-meter per-measure-unit/duration-second")))
    }
    @Test fun syntaxErrorsKeepTokenWindow() {
        val exception = assertFailsWith<SkeletonSyntaxException> { NumberSkeletonCore.parseSkeleton("percent unknown") }
        assertEquals("Syntax error in skeleton string: Unknown stem: percent [unknown] ", exception.message)
        assertFailsWith<SkeletonSyntaxException> { NumberSkeletonCore.parseSkeleton("scientific/") }
        assertFailsWith<SkeletonSyntaxException> { NumberSkeletonCore.parseSkeleton("precision-increment/x") }
        assertEquals("", NumberSkeletonCore.generate(NumberSkeletonCore.parseSkeleton("base-unit base-unit")))
    }
}
