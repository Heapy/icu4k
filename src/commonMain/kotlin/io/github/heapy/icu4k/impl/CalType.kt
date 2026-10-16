// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** ICU calendar types, in the source preference enumeration order. */
enum class CalType(private val typeID: String) {
    GREGORIAN("gregorian"), ISO8601("iso8601"),
    BUDDHIST("buddhist"), CHINESE("chinese"), COPTIC("coptic"), DANGI("dangi"),
    ETHIOPIC("ethiopic"), ETHIOPIC_AMETE_ALEM("ethiopic-amete-alem"), HEBREW("hebrew"), INDIAN("indian"),
    ISLAMIC("islamic"), ISLAMIC_CIVIL("islamic-civil"), ISLAMIC_RGSA("islamic-rgsa"),
    ISLAMIC_TBLA("islamic-tbla"), ISLAMIC_UMALQURA("islamic-umalqura"),
    JAPANESE("japanese"), PERSIAN("persian"), ROC("roc");
    fun getId(): String = typeID
}
