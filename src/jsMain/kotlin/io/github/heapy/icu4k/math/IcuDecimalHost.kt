package io.github.heapy.icu4k.math
internal actual fun icuDecimalToFloat(text: String): Float = icuDecimalFloatFromCandidate(text, js("Math.fround(Number(text))"))
