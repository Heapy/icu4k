package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import kotlin.test.*

class PadderPropertiesTest {
    @Test fun factoryCapturesSettingsAndPadsWithAffixes() {
        val properties = DecimalFormatProperties().setFormatWidth(6).setPadString("😀").setPadPosition(Padder.PadPosition.BEFORE_PREFIX)
        val padder = Padder.forProperties(properties)
        properties.clear()
        val output = FormattedStringBuilder().apply { append("12", null) }
        padder.padAndApply(ConstantAffixModifier("[", "]", null, false), ConstantAffixModifier.EMPTY, output, 0, output.length)
        assertEquals("😀😀[12]", output.toString())
        assertFalse(Padder.forProperties(properties).isValid())
    }
}
