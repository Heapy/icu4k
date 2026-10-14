package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.FormatField

internal actual fun formatFieldClassDescription(field: FormatField): String = field.javaClass.toString()
