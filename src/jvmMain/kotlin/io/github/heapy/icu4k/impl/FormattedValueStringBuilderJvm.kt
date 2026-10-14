package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import java.text.AttributedCharacterIterator
import java.text.AttributedString

internal actual fun formatFieldClassDescription(field: FormatField): String = field.javaClass.toString()
fun FormattedValueStringBuilderImpl.toCharacterIterator(self: FormattedStringBuilder, numericField: FormatField?): AttributedCharacterIterator {
    val position = ConstrainedFieldPosition()
    val attributed = AttributedString(self.toString())
    while (nextPosition(self, position, numericField)) {
        attributed.addAttribute(position.getField(), position.getFieldValue() ?: position.getField(), position.getStart(), position.getLimit())
    }
    return attributed.iterator
}
