// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Java property-bundle syntax and UTF-8/Latin-1 decoding for non-JVM providers. */
internal object JavaPropertiesBundle {
    private val encoding = javaPropertyResourceEncoding()?.uppercase()
    fun read(bytes: ByteArray, requestedEncoding: String? = encoding): JavaResourceBundle {
        val text = if (requestedEncoding == "ISO-8859-1") latin1(bytes) else try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (failure: CharacterCodingException) {
            if (requestedEncoding == "UTF-8") throw failure
            latin1(bytes)
        }
        return MapJavaResourceBundle(parse(text))
    }
    private fun latin1(bytes: ByteArray): String = CharArray(bytes.size) { (bytes[it].toInt() and 255).toChar() }.concatToString()
    private fun whitespace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\u000c'
    internal fun parse(text: String): Map<String, Any> {
        val values = HashMap<String, Any>()
        var position = 0
        var logical = StringBuilder()
        var continuing = false
        while (position < text.length) {
            val start = position
            while (position < text.length && text[position] != '\r' && text[position] != '\n') ++position
            var line = text.substring(start, position)
            var terminatorLength = 0
            if (position < text.length) {
                terminatorLength = 1
                val newline = text[position++]
                if (newline == '\r' && position < text.length && text[position] == '\n') { ++position; terminatorLength = 2 }
            }
            line = line.trimStart(::whitespace)
            if (logical.isEmpty() && (line.isEmpty() || line[0] == '#' || line[0] == '!')) continue
            logical.append(line)
            var slashes = 0
            var i = logical.length - 1
            while (i >= 0 && logical[i--] == '\\') ++slashes
            continuing = slashes and 1 != 0
            if (continuing) {
                logical.setLength(logical.length - 1)
                if (position == text.length && terminatorLength != 2) {
                    addLine(logical.toString(), values); logical = StringBuilder()
                }
            } else { addLine(logical.toString(), values); logical = StringBuilder() }
        }
        if (logical.isNotEmpty()) addLine(logical.toString(), values)
        return values
    }
    private fun addLine(line: String, values: MutableMap<String, Any>) {
        var keyLimit = 0
        var valueStart = line.length
        var separator = false
        var slash = false
        while (keyLimit < line.length) {
            val c = line[keyLimit]
            if ((c == '=' || c == ':') && !slash) { valueStart = keyLimit + 1; separator = true; break }
            if (whitespace(c) && !slash) { valueStart = keyLimit + 1; break }
            slash = if (c == '\\') !slash else false
            ++keyLimit
        }
        while (valueStart < line.length) {
            val c = line[valueStart]
            if (!whitespace(c)) {
                if (!separator && (c == '=' || c == ':')) separator = true else break
            }
            ++valueStart
        }
        values[unescape(line.substring(0, keyLimit))] = unescape(line.substring(valueStart))
    }
    private fun unescape(text: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < text.length) {
            var c = text[index++]
            if (c == '\\') {
                c = text[index++]
                if (c == 'u') {
                    if (index > text.length - 4) throw IllegalArgumentException("Malformed \\uxxxx encoding.")
                    var value = 0
                    repeat(4) {
                        val digit = text[index++].digitToIntOrNull(16)
                        if (digit == null || digit !in 0..15 || text[index - 1].code > 127) throw IllegalArgumentException("Malformed \\uxxxx encoding.")
                        value = (value shl 4) or digit
                    }
                    result.append(value.toChar()); continue
                }
                c = when (c) { 't' -> '\t'; 'r' -> '\r'; 'n' -> '\n'; 'f' -> '\u000c'; else -> c }
            }
            result.append(c)
        }
        return result.toString()
    }
}
