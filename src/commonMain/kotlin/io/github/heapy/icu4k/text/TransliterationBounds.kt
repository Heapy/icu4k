// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Java parser failures must not become JS undefined values or clamped substring ranges. */
internal fun transliterationCharAt(text: String,index: Int): Char {
    if(index<0 || index>=text.length)throw IndexOutOfBoundsException("String index out of range: $index")
    return text[index]
}
internal fun transliterationSubstring(text: String,start: Int,limit: Int): String {
    if(start<0 || limit>text.length || start>limit)throw IndexOutOfBoundsException("Invalid substring range: $start, $limit")
    return text.substring(start,limit)
}
