// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Extracted MeasureUnit.MeasurePrefix for the shared unit parser. */
enum class MeasurePrefix(private val power: Int, private val identifier: String, private val base: Int) {
    QUETTA(30, "quetta", 10),
    RONNA(27, "ronna", 10),
    YOTTA(24, "yotta", 10),
    ZETTA(21, "zetta", 10),
    EXA(18, "exa", 10),
    PETA(15, "peta", 10),
    TERA(12, "tera", 10),
    GIGA(9, "giga", 10),
    MEGA(6, "mega", 10),
    KILO(3, "kilo", 10),
    HECTO(2, "hecto", 10),
    DEKA(1, "deka", 10),
    ONE(0, "", 10),
    DECI(-1, "deci", 10),
    CENTI(-2, "centi", 10),
    MILLI(-3, "milli", 10),
    MICRO(-6, "micro", 10),
    NANO(-9, "nano", 10),
    PICO(-12, "pico", 10),
    FEMTO(-15, "femto", 10),
    ATTO(-18, "atto", 10),
    ZEPTO(-21, "zepto", 10),
    YOCTO(-24, "yocto", 10),
    RONTO(-27, "ronto", 10),
    QUECTO(-30, "quecto", 10),
    KIBI(1, "kibi", 1024),
    MEBI(2, "mebi", 1024),
    GIBI(3, "gibi", 1024),
    TEBI(4, "tebi", 1024),
    PEBI(5, "pebi", 1024),
    EXBI(6, "exbi", 1024),
    ZEBI(7, "zebi", 1024),
    YOBI(8, "yobi", 1024);
    @Deprecated("This API is ICU internal only.") fun getIdentifier(): String = identifier
    fun getBase(): Int = base
    fun getPower(): Int = power
}

enum class UnitComplexity { SINGLE, COMPOUND, MIXED }
