package io.github.heapy.icu4k.text
internal actual fun stringPrepExceptionHashCode(): Int {
    if(StringPrepParseException::class.java.desiredAssertionStatus())throw AssertionError("hashCode not designed")
    return 42
}
