// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class RelativeDateTimeFormatterTest {
    private data class Case(val locale: String, val style: RelativeDateTimeFormatter.Style, val expected: List<String>)
    // Captured from pinned ICU4J 78.3, including field ranges and combination punctuation.
    private val cases = listOf(
        Case("en", RelativeDateTimeFormatter.Style.LONG, listOf("0 days ago", "yesterday", "in 2 days", "in 1.5 days", "Wednesday", "yesterday, 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:11")),
        Case("en", RelativeDateTimeFormatter.Style.SHORT, listOf("0 days ago", "yesterday", "in 2 days", "in 1.5 days", "We", "yesterday, 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:11")),
        Case("en", RelativeDateTimeFormatter.Style.NARROW, listOf("0d ago", "yesterday", "in 2d", "in 1.5d", "W", "yesterday, 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):6:7")),
        Case("en_GB", RelativeDateTimeFormatter.Style.LONG, listOf("0 days ago", "yesterday", "in 2 days", "in 1.5 days", "Wednesday", "yesterday, 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:11")),
        Case("en_GB", RelativeDateTimeFormatter.Style.SHORT, listOf("0 days ago", "yesterday", "in 2 days", "in 1.5 days", "We", "yesterday, 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:11")),
        Case("en_GB", RelativeDateTimeFormatter.Style.NARROW, listOf("0 days ago", "yesterday", "in 2 days", "in 1.5 days", "W", "yesterday, 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:11")),
        Case("fr", RelativeDateTimeFormatter.Style.LONG, listOf("il y a 0 jour", "hier", "apr\u00e8s-demain", "dans 1,5 jour", "mercredi", "yesterday, 15:30", "literal):0:4;integer):5:6;decimal separator):6:7;fraction):7:8;numeric):5:8;literal):9:13")),
        Case("fr", RelativeDateTimeFormatter.Style.SHORT, listOf("il y a 0\u00a0j", "hier", "apr\u00e8s-demain", "dans 1,5\u00a0j", "me", "yesterday, 15:30", "literal):0:4;integer):5:6;decimal separator):6:7;fraction):7:8;numeric):5:8;literal):9:10")),
        Case("fr", RelativeDateTimeFormatter.Style.NARROW, listOf("-0 j", "hier", "apr\u00e8s-demain", "+1,5 j", "M", "yesterday, 15:30", "literal):0:1;integer):1:2;decimal separator):2:3;fraction):3:4;numeric):1:4;literal):5:6")),
        Case("fr_CA", RelativeDateTimeFormatter.Style.LONG, listOf("il y a 0 jour", "hier", "apr\u00e8s-demain", "dans 1,5 jour", "mercredi", "yesterday, 15:30", "literal):0:4;integer):5:6;decimal separator):6:7;fraction):7:8;numeric):5:8;literal):9:13")),
        Case("fr_CA", RelativeDateTimeFormatter.Style.SHORT, listOf("il y a 0\u00a0j", "hier", "apr\u00e8s-demain", "dans 1,5\u00a0j", "me", "yesterday, 15:30", "literal):0:4;integer):5:6;decimal separator):6:7;fraction):7:8;numeric):5:8;literal):9:10")),
        Case("fr_CA", RelativeDateTimeFormatter.Style.NARROW, listOf("-0 j", "hier", "apr\u00e8s-demain", "+1,5 j", "M", "yesterday, 15:30", "literal):0:1;integer):1:2;decimal separator):2:3;fraction):3:4;numeric):1:4;literal):5:6")),
        Case("de", RelativeDateTimeFormatter.Style.LONG, listOf("vor 0 Tagen", "gestern", "\u00fcbermorgen", "in 1,5 Tagen", "Mittwoch", "yesterday, 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:12")),
        Case("de", RelativeDateTimeFormatter.Style.SHORT, listOf("vor 0 Tagen", "gestern", "\u00fcbermorgen", "in 1,5 Tagen", "Mi.", "yesterday, 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:12")),
        Case("de", RelativeDateTimeFormatter.Style.NARROW, listOf("vor 0 Tagen", "gestern", "\u00fcbermorgen", "in 1,5 Tagen", "M", "yesterday, 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:12")),
        Case("ru", RelativeDateTimeFormatter.Style.LONG, listOf("0 \u0434\u043d\u0435\u0439 \u043d\u0430\u0437\u0430\u0434", "\u0432\u0447\u0435\u0440\u0430", "\u043f\u043e\u0441\u043b\u0435\u0437\u0430\u0432\u0442\u0440\u0430", "\u0447\u0435\u0440\u0435\u0437 1,5 \u0434\u043d\u044f", "\u0441\u0440\u0435\u0434\u0430", "yesterday, 15:30", "literal):0:5;integer):6:7;decimal separator):7:8;fraction):8:9;numeric):6:9;literal):10:13")),
        Case("ru", RelativeDateTimeFormatter.Style.SHORT, listOf("0 \u0434\u043d. \u043d\u0430\u0437\u0430\u0434", "\u0432\u0447\u0435\u0440\u0430", "\u043f\u043e\u0441\u043b\u0435\u0437\u0430\u0432\u0442\u0440\u0430", "\u0447\u0435\u0440\u0435\u0437 1,5 \u0434\u043d.", "\u0441\u0440", "yesterday, 15:30", "literal):0:5;integer):6:7;decimal separator):7:8;fraction):8:9;numeric):6:9;literal):10:13")),
        Case("ru", RelativeDateTimeFormatter.Style.NARROW, listOf("-0 \u0434\u043d.", "\u0432\u0447\u0435\u0440\u0430", "\u043f\u043e\u0441\u043b\u0435\u0437\u0430\u0432\u0442\u0440\u0430", "+1,5 \u0434\u043d.", "\u0421", "yesterday, 15:30", "literal):0:1;integer):1:2;decimal separator):2:3;fraction):3:4;numeric):1:4;literal):5:8")),
        Case("pl", RelativeDateTimeFormatter.Style.LONG, listOf("0 dni temu", "wczoraj", "pojutrze", "za 1,5 dnia", "\u015broda", "yesterday, 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:11")),
        Case("pl", RelativeDateTimeFormatter.Style.SHORT, listOf("0 dni temu", "wczoraj", "pojutrze", "za 1,5 dnia", "\u015bro", "yesterday, 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:11")),
        Case("pl", RelativeDateTimeFormatter.Style.NARROW, listOf("0 dni temu", "wcz.", "pojutrze", "za 1,5 dnia", "\u015a", "yesterday, 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:11")),
        Case("cs", RelativeDateTimeFormatter.Style.LONG, listOf("p\u0159ed 0 dny", "v\u010dera", "poz\u00edt\u0159\u00ed", "za 1,5 dne", "st\u0159eda", "yesterday 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:10")),
        Case("cs", RelativeDateTimeFormatter.Style.SHORT, listOf("p\u0159ed 0 dny", "v\u010dera", "poz\u00edt\u0159\u00ed", "za 1,5 dne", "st", "yesterday 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:10")),
        Case("cs", RelativeDateTimeFormatter.Style.NARROW, listOf("p\u0159ed 0 dny", "v\u010dera", "poz\u00edt\u0159\u00ed", "za 1,5 dne", "S", "yesterday 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:10")),
        Case("sr", RelativeDateTimeFormatter.Style.LONG, listOf("\u043f\u0440\u0435 0 \u0434\u0430\u043d\u0430", "\u0458\u0443\u0447\u0435", "\u043f\u0440\u0435\u043a\u043e\u0441\u0443\u0442\u0440\u0430", "\u0437\u0430 1,5 \u0434\u0430\u043d\u0430", "\u0441\u0440\u0435\u0434\u0430", "yesterday 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:11")),
        Case("sr", RelativeDateTimeFormatter.Style.SHORT, listOf("\u043f\u0440\u0435 0 \u0434.", "\u0458\u0443\u0447\u0435", "\u043f\u0440\u0435\u043a\u043e\u0441\u0443\u0442\u0440\u0430", "\u0437\u0430 1,5 \u0434.", "\u0441\u0440", "yesterday 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:9")),
        Case("sr", RelativeDateTimeFormatter.Style.NARROW, listOf("\u043f\u0440\u0435 0 \u0434.", "\u0458\u0443\u0447\u0435", "\u043f\u0440\u0435\u043a\u043e\u0441\u0443\u0442\u0440\u0430", "\u0437\u0430 1,5 \u0434.", "\u0441", "yesterday 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:9")),
        Case("sr_Latn", RelativeDateTimeFormatter.Style.LONG, listOf("pre 0 dana", "ju\u010de", "prekosutra", "za 1,5 dana", "sreda", "yesterday 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:11")),
        Case("sr_Latn", RelativeDateTimeFormatter.Style.SHORT, listOf("pre 0 d.", "ju\u010de", "prekosutra", "za 1,5 d.", "sr", "yesterday 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:9")),
        Case("sr_Latn", RelativeDateTimeFormatter.Style.NARROW, listOf("pre 0 d.", "ju\u010de", "prekosutra", "za 1,5 d.", "s", "yesterday 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:9")),
        Case("ar", RelativeDateTimeFormatter.Style.LONG, listOf("\u0642\u0628\u0644 0 \u064a\u0648\u0645", "\u0623\u0645\u0633", "\u0628\u0639\u062f \u0627\u0644\u063a\u062f", "\u062e\u0644\u0627\u0644 1.5 \u064a\u0648\u0645", "\u0627\u0644\u0623\u0631\u0628\u0639\u0627\u0621", "yesterday\u060c 15:30", "literal):0:4;integer):5:6;decimal separator):6:7;fraction):7:8;numeric):5:8;literal):9:12")),
        Case("ar", RelativeDateTimeFormatter.Style.SHORT, listOf("\u0642\u0628\u0644 0 \u064a\u0648\u0645", "\u0623\u0645\u0633", "\u0628\u0639\u062f \u0627\u0644\u063a\u062f", "\u062e\u0644\u0627\u0644 1.5 \u064a\u0648\u0645", "\u0623\u0631\u0628\u0639\u0627\u0621", "yesterday\u060c 15:30", "literal):0:4;integer):5:6;decimal separator):6:7;fraction):7:8;numeric):5:8;literal):9:12")),
        Case("ar", RelativeDateTimeFormatter.Style.NARROW, listOf("\u0642\u0628\u0644 0 \u064a\u0648\u0645", "\u0623\u0645\u0633", "\u0628\u0639\u062f \u0627\u0644\u063a\u062f", "\u062e\u0644\u0627\u0644 1.5 \u064a\u0648\u0645", "\u0631", "yesterday\u060c 15:30", "literal):0:4;integer):5:6;decimal separator):6:7;fraction):7:8;numeric):5:8;literal):9:12")),
        Case("he", RelativeDateTimeFormatter.Style.LONG, listOf("\u05dc\u05e4\u05e0\u05d9 0 \u05d9\u05de\u05d9\u05dd", "\u05d0\u05ea\u05de\u05d5\u05dc", "\u05de\u05d7\u05e8\u05ea\u05d9\u05d9\u05dd", "\u05d1\u05e2\u05d5\u05d3 1.5 \u05d9\u05de\u05d9\u05dd", "\u05d9\u05d5\u05dd \u05e8\u05d1\u05d9\u05e2\u05d9", "yesterday, 15:30", "literal):0:4;integer):5:6;decimal separator):6:7;fraction):7:8;numeric):5:8;literal):9:13")),
        Case("he", RelativeDateTimeFormatter.Style.SHORT, listOf("\u05dc\u05e4\u05e0\u05d9 0 \u05d9\u05de\u05d9\u05dd", "\u05d0\u05ea\u05de\u05d5\u05dc", "\u05de\u05d7\u05e8\u05ea\u05d9\u05d9\u05dd", "\u05d1\u05e2\u05d5\u05d3 1.5 \u05d9\u05de\u05d9\u05dd", "\u05d3\u05f3", "yesterday, 15:30", "literal):0:4;integer):5:6;decimal separator):6:7;fraction):7:8;numeric):5:8;literal):9:13")),
        Case("he", RelativeDateTimeFormatter.Style.NARROW, listOf("\u05dc\u05e4\u05e0\u05d9 0 \u05d9\u05de\u05d9\u05dd", "\u05d0\u05ea\u05de\u05d5\u05dc", "\u05de\u05d7\u05e8\u05ea\u05d9\u05d9\u05dd", "\u05d1\u05e2\u05d5\u05d3 1.5 \u05d9\u05de\u05d9\u05dd", "\u05d3\u05f3", "yesterday, 15:30", "literal):0:4;integer):5:6;decimal separator):6:7;fraction):7:8;numeric):5:8;literal):9:13")),
        Case("ja", RelativeDateTimeFormatter.Style.LONG, listOf("0 \u65e5\u524d", "\u6628\u65e5", "\u660e\u5f8c\u65e5", "1.5 \u65e5\u5f8c", "\u6c34\u66dc\u65e5", "yesterday 15:30", "integer):0:1;decimal separator):1:2;fraction):2:3;numeric):0:3;literal):4:6")),
        Case("ja", RelativeDateTimeFormatter.Style.SHORT, listOf("0 \u65e5\u524d", "\u6628\u65e5", "\u660e\u5f8c\u65e5", "1.5 \u65e5\u5f8c", "\u6c34", "yesterday 15:30", "integer):0:1;decimal separator):1:2;fraction):2:3;numeric):0:3;literal):4:6")),
        Case("ja", RelativeDateTimeFormatter.Style.NARROW, listOf("0\u65e5\u524d", "\u6628\u65e5", "\u660e\u5f8c\u65e5", "1.5\u65e5\u5f8c", "\u6c34", "yesterday 15:30", "integer):0:1;decimal separator):1:2;fraction):2:3;numeric):0:3;literal):3:5")),
        Case("zh_Hant", RelativeDateTimeFormatter.Style.LONG, listOf("0 \u5929\u524d", "\u6628\u5929", "\u5f8c\u5929", "1.5 \u5929\u5f8c", "\u661f\u671f\u4e09", "yesterday 15:30", "integer):0:1;decimal separator):1:2;fraction):2:3;numeric):0:3;literal):4:6")),
        Case("zh_Hant", RelativeDateTimeFormatter.Style.SHORT, listOf("0 \u5929\u524d", "\u6628\u5929", "\u5f8c\u5929", "1.5 \u5929\u5f8c", "\u4e09", "yesterday 15:30", "integer):0:1;decimal separator):1:2;fraction):2:3;numeric):0:3;literal):4:6")),
        Case("zh_Hant", RelativeDateTimeFormatter.Style.NARROW, listOf("0 \u5929\u524d", "\u6628\u5929", "\u5f8c\u5929", "1.5 \u5929\u5f8c", "\u4e09", "yesterday 15:30", "integer):0:1;decimal separator):1:2;fraction):2:3;numeric):0:3;literal):4:6")),
        Case("fi", RelativeDateTimeFormatter.Style.LONG, listOf("0 p\u00e4iv\u00e4\u00e4 sitten", "eilen", "ylihuomenna", "1,5 p\u00e4iv\u00e4n p\u00e4\u00e4st\u00e4", "keskiviikko", "yesterday 'klo' 15:30", "integer):0:1;decimal separator):1:2;fraction):2:3;numeric):0:3;literal):4:17")),
        Case("fi", RelativeDateTimeFormatter.Style.SHORT, listOf("0 pv sitten", "eilen", "ylihuom.", "1,5 pv p\u00e4\u00e4st\u00e4", "ke", "yesterday 'klo' 15:30", "integer):0:1;decimal separator):1:2;fraction):2:3;numeric):0:3;literal):4:13")),
        Case("fi", RelativeDateTimeFormatter.Style.NARROW, listOf("0 pv sitten", "eilen", "ylihuom.", "1,5 pv p\u00e4\u00e4st\u00e4", "K", "yesterday 'klo' 15:30", "integer):0:1;decimal separator):1:2;fraction):2:3;numeric):0:3;literal):4:13")),
        Case("tr", RelativeDateTimeFormatter.Style.LONG, listOf("0 g\u00fcn \u00f6nce", "d\u00fcn", "\u00f6b\u00fcr g\u00fcn", "1,5 g\u00fcn sonra", "\u00c7ar\u015famba", "yesterday 15:30", "integer):0:1;decimal separator):1:2;fraction):2:3;numeric):0:3;literal):4:13")),
        Case("tr", RelativeDateTimeFormatter.Style.SHORT, listOf("0 g\u00fcn \u00f6nce", "d\u00fcn", "\u00f6b\u00fcr g\u00fcn", "1,5 g\u00fcn sonra", "\u00c7a", "yesterday 15:30", "integer):0:1;decimal separator):1:2;fraction):2:3;numeric):0:3;literal):4:13")),
        Case("tr", RelativeDateTimeFormatter.Style.NARROW, listOf("0 g\u00fcn \u00f6nce", "d\u00fcn", "\u00f6b\u00fcr g\u00fcn", "1,5 g\u00fcn sonra", "\u00c7", "yesterday 15:30", "integer):0:1;decimal separator):1:2;fraction):2:3;numeric):0:3;literal):4:13")),
        Case("es", RelativeDateTimeFormatter.Style.LONG, listOf("hace 0 d\u00edas", "ayer", "pasado ma\u00f1ana", "dentro de 1,5 d\u00edas", "mi\u00e9rcoles", "yesterday, 15:30", "literal):0:9;integer):10:11;decimal separator):11:12;fraction):12:13;numeric):10:13;literal):14:18")),
        Case("es", RelativeDateTimeFormatter.Style.SHORT, listOf("hace 0 d", "ayer", "pasado ma\u00f1ana", "dentro de 1,5 d", "MI", "yesterday, 15:30", "literal):0:9;integer):10:11;decimal separator):11:12;fraction):12:13;numeric):10:13;literal):14:15")),
        Case("es", RelativeDateTimeFormatter.Style.NARROW, listOf("hace 0 d", "ayer", "pasado ma\u00f1ana", "dentro de 1,5 d", "X", "yesterday, 15:30", "literal):0:9;integer):10:11;decimal separator):11:12;fraction):12:13;numeric):10:13;literal):14:15")),
        Case("sw", RelativeDateTimeFormatter.Style.LONG, listOf("siku 0 zilizopita", "jana", "kesho kutwa", "baada ya siku 1.5", "Jumatano", "yesterday, 15:30", "literal):0:13;integer):14:15;decimal separator):15:16;fraction):16:17;numeric):14:17")),
        Case("sw", RelativeDateTimeFormatter.Style.SHORT, listOf("siku 0 zilizopita", "jana", "kesho kutwa", "baada ya siku 1.5", "Jumatano", "yesterday, 15:30", "literal):0:13;integer):14:15;decimal separator):15:16;fraction):16:17;numeric):14:17")),
        Case("sw", RelativeDateTimeFormatter.Style.NARROW, listOf("siku 0 zilizopita", "jana", "kesho kutwa", "baada ya siku 1.5", "W", "yesterday, 15:30", "literal):0:13;integer):14:15;decimal separator):15:16;fraction):16:17;numeric):14:17")),
        Case("th@calendar=buddhist", RelativeDateTimeFormatter.Style.LONG, listOf("0 \u0e27\u0e31\u0e19\u0e17\u0e35\u0e48\u0e1c\u0e48\u0e32\u0e19\u0e21\u0e32", "\u0e40\u0e21\u0e37\u0e48\u0e2d\u0e27\u0e32\u0e19", "\u0e21\u0e30\u0e23\u0e37\u0e19\u0e19\u0e35\u0e49", "\u0e43\u0e19\u0e2d\u0e35\u0e01 1.5 \u0e27\u0e31\u0e19", "\u0e27\u0e31\u0e19\u0e1e\u0e38\u0e18", "yesterday 15:30", "literal):0:5;integer):6:7;decimal separator):7:8;fraction):8:9;numeric):6:9;literal):10:13")),
        Case("th@calendar=buddhist", RelativeDateTimeFormatter.Style.SHORT, listOf("0 \u0e27\u0e31\u0e19\u0e17\u0e35\u0e48\u0e41\u0e25\u0e49\u0e27", "\u0e40\u0e21\u0e37\u0e48\u0e2d\u0e27\u0e32\u0e19", "\u0e21\u0e30\u0e23\u0e37\u0e19\u0e19\u0e35\u0e49", "\u0e43\u0e19 1.5 \u0e27\u0e31\u0e19", "\u0e1e.", "yesterday 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:10")),
        Case("th@calendar=buddhist", RelativeDateTimeFormatter.Style.NARROW, listOf("0 \u0e27\u0e31\u0e19\u0e17\u0e35\u0e48\u0e41\u0e25\u0e49\u0e27", "\u0e40\u0e21\u0e37\u0e48\u0e2d\u0e27\u0e32\u0e19", "\u0e21\u0e30\u0e23\u0e37\u0e19\u0e19\u0e35\u0e49", "\u0e43\u0e19 1.5 \u0e27\u0e31\u0e19", "\u0e1e", "yesterday 15:30", "literal):0:2;integer):3:4;decimal separator):4:5;fraction):5:6;numeric):3:6;literal):7:10")),
        Case("fa", RelativeDateTimeFormatter.Style.LONG, listOf("\u06f0 \u0631\u0648\u0632 \u067e\u06cc\u0634", "\u062f\u06cc\u0631\u0648\u0632", "\u067e\u0633\u200c\u0641\u0631\u062f\u0627", "\u06f1\u066b\u06f5 \u0631\u0648\u0632 \u062f\u06cc\u06af\u0631", "\u0686\u0647\u0627\u0631\u0634\u0646\u0628\u0647", "yesterday\u060c 15:30", "integer):0:1;decimal separator):1:2;fraction):2:3;numeric):0:3;literal):4:12")),
        Case("fa", RelativeDateTimeFormatter.Style.SHORT, listOf("\u06f0 \u0631\u0648\u0632 \u067e\u06cc\u0634", "\u062f\u06cc\u0631\u0648\u0632", "\u067e\u0633\u200c\u0641\u0631\u062f\u0627", "\u06f1\u066b\u06f5 \u0631\u0648\u0632 \u062f\u06cc\u06af\u0631", "\u06f4\u0634", "yesterday\u060c 15:30", "integer):0:1;decimal separator):1:2;fraction):2:3;numeric):0:3;literal):4:12")),
        Case("fa", RelativeDateTimeFormatter.Style.NARROW, listOf("\u06f0 \u0631\u0648\u0632 \u067e\u06cc\u0634", "\u062f\u06cc\u0631\u0648\u0632", "\u067e\u0633\u200c\u0641\u0631\u062f\u0627", "\u06f1\u066b\u06f5 \u0631\u0648\u0632 \u0628\u0639\u062f", "\u0686", "yesterday\u060c 15:30", "integer):0:1;decimal separator):1:2;fraction):2:3;numeric):0:3;literal):4:11")),
        Case("root", RelativeDateTimeFormatter.Style.LONG, listOf("-0 d", "yesterday", "+2 d", "+1.5 d", "Wed", "yesterday 15:30", "literal):0:1;integer):1:2;decimal separator):2:3;fraction):3:4;numeric):1:4;literal):5:6")),
        Case("root", RelativeDateTimeFormatter.Style.SHORT, listOf("-0 d", "yesterday", "+2 d", "+1.5 d", "Wed", "yesterday 15:30", "literal):0:1;integer):1:2;decimal separator):2:3;fraction):3:4;numeric):1:4;literal):5:6")),
        Case("root", RelativeDateTimeFormatter.Style.NARROW, listOf("-0 d", "yesterday", "+2 d", "+1.5 d", "W", "yesterday 15:30", "literal):0:1;integer):1:2;decimal separator):2:3;fraction):3:4;numeric):1:4;literal):5:6")),
    )
    @Test fun sourceLocaleStyleAndFieldGoldens() {
        for ((locale, style, expected) in cases) {
            val f = RelativeDateTimeFormatter.getInstance(ULocale(locale), null, style, DisplayContext.CAPITALIZATION_NONE)
            val value = f.formatToValue(1.5, RelativeDateTimeFormatter.Direction.NEXT, RelativeDateTimeFormatter.RelativeUnit.DAYS)
            val p = ConstrainedFieldPosition()
            val fields = mutableListOf<String>()
            while (value.nextPosition(p)) fields.add("${p.getField().toString().substringAfterLast('(')}:${p.getStart()}:${p.getLimit()}")
            val actual = listOf(f.formatNumeric(-0.0, RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY),
                f.format(-1.0, RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY),
                f.format(2.0, RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY), value.toString(),
                f.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.WEDNESDAY)!!,
                f.combineDateAndTime("yesterday", "15:30"), fields.joinToString(";"))
            assertEquals(expected, actual, "$locale/$style")
            val buffer = StringBuilder("prefix:")
            assertSame(buffer, value.appendTo(buffer))
            assertEquals("prefix:" + expected[3], buffer.toString())
            assertEquals(expected[3].length, value.length)
            assertEquals(expected[3], value.subSequence(0, value.length))
            val numeric = ConstrainedFieldPosition().also { it.constrainField(RelativeDateTimeFormatter.Field.NUMERIC) }
            assertTrue(value.nextPosition(numeric))
            assertTrue(numeric.getStart() < numeric.getLimit())
        }
    }
    @Test fun roundedPluralsCloneIsolationAndCapitalization() {
        val nf = DecimalFormat("0.00", DecimalFormatSymbols(ULocale.US))
        val f = RelativeDateTimeFormatter.getInstance(ULocale.US, nf)
        nf.applyPattern("0")
        assertEquals("in 1.00 days", f.format(1.0, RelativeDateTimeFormatter.Direction.NEXT, RelativeDateTimeFormatter.RelativeUnit.DAYS))
        (f.getNumberFormat() as DecimalFormat).applyPattern("0.0000")
        assertEquals("in 1.00 days", f.format(1.0, RelativeDateTimeFormatter.Direction.NEXT, RelativeDateTimeFormatter.RelativeUnit.DAYS))
        val sentence = RelativeDateTimeFormatter.getInstance(ULocale.US, null, RelativeDateTimeFormatter.Style.LONG,
            DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE)
        assertEquals("Yesterday", sentence.format(-1.0, RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY))
        assertFailsWith<UnsupportedOperationException> { sentence.formatToValue(1.0, null, null) }
        val menu = RelativeDateTimeFormatter.getInstance(ULocale.US, null, RelativeDateTimeFormatter.Style.LONG,
            DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU)
        assertEquals("yesterday", menu.format(-1.0, RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY))
        assertEquals("in 0 days", RelativeDateTimeFormatter.getInstance(ULocale.US).formatNumeric(0.0, RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY))
    }
    @Test fun sidewaysStyleAndPluralFallback() {
        val f = RelativeDateTimeFormatter.getInstance(ULocale("en_GB"), null, RelativeDateTimeFormatter.Style.NARROW,
            DisplayContext.CAPITALIZATION_NONE)
        assertEquals("in 3 mo", f.format(3.0, RelativeDateTimeFormatter.Direction.NEXT, RelativeDateTimeFormatter.RelativeUnit.MONTHS))
        assertEquals("1 qtr ago", f.format(1.0, RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.QUARTERS))
        assertEquals("3 qtr ago", f.format(3.0, RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.QUARTERS))
        assertEquals("in 6 qtr", f.format(6.0, RelativeDateTimeFormatter.Direction.NEXT, RelativeDateTimeFormatter.RelativeUnit.QUARTERS))
    }

}
