// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import kotlin.test.*

class LocaleMatcherTest {
    private data class Case(val config: Int, val desired: String, val result: String, val matcher: String, val builder: String)
    // All values captured from pinned ICU4J 78.3.
    private val cases = listOf(
        Case(0, "en_AU, fr_CA", "en_AU|en_GB|0|0|en_AU", "{LocaleMatcher supportedLSRs={en-Latn-GB, en-Latn-US, fr-Latn-FR, de-Latn-DE, zh-Hant-TW, zh-Hans-CN} default=en_GB threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN]}}"),
        Case(0, "de_CH_1901@calendar=buddhist;numbers=arab", "de_CH_1901@calendar=buddhist;numbers=arab|de_DE|0|3|de_CH_1901@calendar=buddhist;numbers=arab", "{LocaleMatcher supportedLSRs={en-Latn-GB, en-Latn-US, fr-Latn-FR, de-Latn-DE, zh-Hant-TW, zh-Hans-CN} default=en_GB threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN]}}"),
        Case(0, "zh_HK, ja", "zh_HK|zh_Hant_TW|0|4|zh_Hant_HK", "{LocaleMatcher supportedLSRs={en-Latn-GB, en-Latn-US, fr-Latn-FR, de-Latn-DE, zh-Hant-TW, zh-Hans-CN} default=en_GB threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN]}}"),
        Case(0, "no, nn", "<null>|en_GB|-1|-1|en_GB", "{LocaleMatcher supportedLSRs={en-Latn-GB, en-Latn-US, fr-Latn-FR, de-Latn-DE, zh-Hant-TW, zh-Hans-CN} default=en_GB threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN]}}"),
        Case(0, "en__PSACCENT, ar_XB", "<null>|en_GB|-1|-1|en_GB", "{LocaleMatcher supportedLSRs={en-Latn-GB, en-Latn-US, fr-Latn-FR, de-Latn-DE, zh-Hant-TW, zh-Hans-CN} default=en_GB threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN]}}"),
        Case(0, "und", "<null>|en_GB|-1|-1|en_GB", "{LocaleMatcher supportedLSRs={en-Latn-GB, en-Latn-US, fr-Latn-FR, de-Latn-DE, zh-Hant-TW, zh-Hans-CN} default=en_GB threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN]}}"),
        Case(0, "@x=abc, ar", "<null>|en_GB|-1|-1|en_GB", "{LocaleMatcher supportedLSRs={en-Latn-GB, en-Latn-US, fr-Latn-FR, de-Latn-DE, zh-Hant-TW, zh-Hans-CN} default=en_GB threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN]}}"),
        Case(0, "ar, sr_Latn", "<null>|en_GB|-1|-1|en_GB", "{LocaleMatcher supportedLSRs={en-Latn-GB, en-Latn-US, fr-Latn-FR, de-Latn-DE, zh-Hant-TW, zh-Hans-CN} default=en_GB threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN]}}"),
        Case(1, "en_AU, fr_CA", "en_AU|en_GB|0|0|en_AU", "{LocaleMatcher supportedLSRs={en-Latn-GB, en-Latn-US, fr-Latn-FR, de-Latn-DE, zh-Hant-TW, zh-Hans-CN} default=en_GB threshold=50 demotion=0}", "{LocaleMatcher.Builder supported={[en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN]} demotion=NONE}"),
        Case(1, "de_CH_1901@calendar=buddhist;numbers=arab", "de_CH_1901@calendar=buddhist;numbers=arab|de_DE|0|3|de_CH_1901@calendar=buddhist;numbers=arab", "{LocaleMatcher supportedLSRs={en-Latn-GB, en-Latn-US, fr-Latn-FR, de-Latn-DE, zh-Hant-TW, zh-Hans-CN} default=en_GB threshold=50 demotion=0}", "{LocaleMatcher.Builder supported={[en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN]} demotion=NONE}"),
        Case(1, "zh_HK, ja", "zh_HK|zh_Hant_TW|0|4|zh_Hant_HK", "{LocaleMatcher supportedLSRs={en-Latn-GB, en-Latn-US, fr-Latn-FR, de-Latn-DE, zh-Hant-TW, zh-Hans-CN} default=en_GB threshold=50 demotion=0}", "{LocaleMatcher.Builder supported={[en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN]} demotion=NONE}"),
        Case(1, "no, nn", "<null>|en_GB|-1|-1|en_GB", "{LocaleMatcher supportedLSRs={en-Latn-GB, en-Latn-US, fr-Latn-FR, de-Latn-DE, zh-Hant-TW, zh-Hans-CN} default=en_GB threshold=50 demotion=0}", "{LocaleMatcher.Builder supported={[en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN]} demotion=NONE}"),
        Case(1, "en__PSACCENT, ar_XB", "<null>|en_GB|-1|-1|en_GB", "{LocaleMatcher supportedLSRs={en-Latn-GB, en-Latn-US, fr-Latn-FR, de-Latn-DE, zh-Hant-TW, zh-Hans-CN} default=en_GB threshold=50 demotion=0}", "{LocaleMatcher.Builder supported={[en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN]} demotion=NONE}"),
        Case(1, "und", "<null>|en_GB|-1|-1|en_GB", "{LocaleMatcher supportedLSRs={en-Latn-GB, en-Latn-US, fr-Latn-FR, de-Latn-DE, zh-Hant-TW, zh-Hans-CN} default=en_GB threshold=50 demotion=0}", "{LocaleMatcher.Builder supported={[en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN]} demotion=NONE}"),
        Case(1, "@x=abc, ar", "<null>|en_GB|-1|-1|en_GB", "{LocaleMatcher supportedLSRs={en-Latn-GB, en-Latn-US, fr-Latn-FR, de-Latn-DE, zh-Hant-TW, zh-Hans-CN} default=en_GB threshold=50 demotion=0}", "{LocaleMatcher.Builder supported={[en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN]} demotion=NONE}"),
        Case(1, "ar, sr_Latn", "<null>|en_GB|-1|-1|en_GB", "{LocaleMatcher supportedLSRs={en-Latn-GB, en-Latn-US, fr-Latn-FR, de-Latn-DE, zh-Hant-TW, zh-Hans-CN} default=en_GB threshold=50 demotion=0}", "{LocaleMatcher.Builder supported={[en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN]} demotion=NONE}"),
        Case(2, "en_AU, fr_CA", "en_AU|en|0|0|en_AU", "{LocaleMatcher supportedLSRs={en-Latn-US, ru-Cyrl-RU, sr-Cyrl-RS, sr-Latn-RS, zh-Hant-TW, zh-Hans-CN, ja-Jpan-JP} default=en threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en, ru, sr_Cyrl, sr_Latn, zh_Hant, zh_Hans, ja]}}"),
        Case(2, "de_CH_1901@calendar=buddhist;numbers=arab", "<null>|en|-1|-1|en", "{LocaleMatcher supportedLSRs={en-Latn-US, ru-Cyrl-RU, sr-Cyrl-RS, sr-Latn-RS, zh-Hant-TW, zh-Hans-CN, ja-Jpan-JP} default=en threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en, ru, sr_Cyrl, sr_Latn, zh_Hant, zh_Hans, ja]}}"),
        Case(2, "zh_HK, ja", "zh_HK|zh_Hant|0|4|zh_Hant_HK", "{LocaleMatcher supportedLSRs={en-Latn-US, ru-Cyrl-RU, sr-Cyrl-RS, sr-Latn-RS, zh-Hant-TW, zh-Hans-CN, ja-Jpan-JP} default=en threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en, ru, sr_Cyrl, sr_Latn, zh_Hant, zh_Hans, ja]}}"),
        Case(2, "no, nn", "<null>|en|-1|-1|en", "{LocaleMatcher supportedLSRs={en-Latn-US, ru-Cyrl-RU, sr-Cyrl-RS, sr-Latn-RS, zh-Hant-TW, zh-Hans-CN, ja-Jpan-JP} default=en threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en, ru, sr_Cyrl, sr_Latn, zh_Hant, zh_Hans, ja]}}"),
        Case(2, "en__PSACCENT, ar_XB", "<null>|en|-1|-1|en", "{LocaleMatcher supportedLSRs={en-Latn-US, ru-Cyrl-RU, sr-Cyrl-RS, sr-Latn-RS, zh-Hant-TW, zh-Hans-CN, ja-Jpan-JP} default=en threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en, ru, sr_Cyrl, sr_Latn, zh_Hant, zh_Hans, ja]}}"),
        Case(2, "und", "<null>|en|-1|-1|en", "{LocaleMatcher supportedLSRs={en-Latn-US, ru-Cyrl-RU, sr-Cyrl-RS, sr-Latn-RS, zh-Hant-TW, zh-Hans-CN, ja-Jpan-JP} default=en threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en, ru, sr_Cyrl, sr_Latn, zh_Hant, zh_Hans, ja]}}"),
        Case(2, "@x=abc, ar", "<null>|en|-1|-1|en", "{LocaleMatcher supportedLSRs={en-Latn-US, ru-Cyrl-RU, sr-Cyrl-RS, sr-Latn-RS, zh-Hant-TW, zh-Hans-CN, ja-Jpan-JP} default=en threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en, ru, sr_Cyrl, sr_Latn, zh_Hant, zh_Hans, ja]}}"),
        Case(2, "ar, sr_Latn", "sr_Latn|sr_Latn|1|3|sr_Latn", "{LocaleMatcher supportedLSRs={en-Latn-US, ru-Cyrl-RU, sr-Cyrl-RS, sr-Latn-RS, zh-Hant-TW, zh-Hans-CN, ja-Jpan-JP} default=en threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en, ru, sr_Cyrl, sr_Latn, zh_Hant, zh_Hans, ja]}}"),
        Case(3, "en_AU, fr_CA", "<null>|nb|-1|-1|nb", "{LocaleMatcher supportedLSRs={nb-Latn-NO, nn-Latn-NO, da-Latn-DK} default=nb direction=ONLY_TWO_WAY threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[nb, nn, da]}}"),
        Case(3, "de_CH_1901@calendar=buddhist;numbers=arab", "<null>|nb|-1|-1|nb", "{LocaleMatcher supportedLSRs={nb-Latn-NO, nn-Latn-NO, da-Latn-DK} default=nb direction=ONLY_TWO_WAY threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[nb, nn, da]}}"),
        Case(3, "zh_HK, ja", "<null>|nb|-1|-1|nb", "{LocaleMatcher supportedLSRs={nb-Latn-NO, nn-Latn-NO, da-Latn-DK} default=nb direction=ONLY_TWO_WAY threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[nb, nn, da]}}"),
        Case(3, "no, nn", "no|nb|0|0|nb", "{LocaleMatcher supportedLSRs={nb-Latn-NO, nn-Latn-NO, da-Latn-DK} default=nb direction=ONLY_TWO_WAY threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[nb, nn, da]}}"),
        Case(3, "en__PSACCENT, ar_XB", "<null>|nb|-1|-1|nb", "{LocaleMatcher supportedLSRs={nb-Latn-NO, nn-Latn-NO, da-Latn-DK} default=nb direction=ONLY_TWO_WAY threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[nb, nn, da]}}"),
        Case(3, "und", "<null>|nb|-1|-1|nb", "{LocaleMatcher supportedLSRs={nb-Latn-NO, nn-Latn-NO, da-Latn-DK} default=nb direction=ONLY_TWO_WAY threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[nb, nn, da]}}"),
        Case(3, "@x=abc, ar", "<null>|nb|-1|-1|nb", "{LocaleMatcher supportedLSRs={nb-Latn-NO, nn-Latn-NO, da-Latn-DK} default=nb direction=ONLY_TWO_WAY threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[nb, nn, da]}}"),
        Case(3, "ar, sr_Latn", "<null>|nb|-1|-1|nb", "{LocaleMatcher supportedLSRs={nb-Latn-NO, nn-Latn-NO, da-Latn-DK} default=nb direction=ONLY_TWO_WAY threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[nb, nn, da]}}"),
        Case(4, "en_AU, fr_CA", "fr_CA|fr_FR@calendar=gregorian|1|1|fr_CA@calendar=gregorian", "{LocaleMatcher supportedLSRs={de-Latn-DE, fr-Latn-FR} default=de_DE@numbers=latn threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[de_DE@numbers=latn, fr_FR@calendar=gregorian]}}"),
        Case(4, "de_CH_1901@calendar=buddhist;numbers=arab", "de_CH_1901@calendar=buddhist;numbers=arab|de_DE@numbers=latn|0|0|de_CH_1901@calendar=buddhist;numbers=arab", "{LocaleMatcher supportedLSRs={de-Latn-DE, fr-Latn-FR} default=de_DE@numbers=latn threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[de_DE@numbers=latn, fr_FR@calendar=gregorian]}}"),
        Case(4, "zh_HK, ja", "<null>|de_DE@numbers=latn|-1|-1|de_DE@numbers=latn", "{LocaleMatcher supportedLSRs={de-Latn-DE, fr-Latn-FR} default=de_DE@numbers=latn threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[de_DE@numbers=latn, fr_FR@calendar=gregorian]}}"),
        Case(4, "no, nn", "<null>|de_DE@numbers=latn|-1|-1|de_DE@numbers=latn", "{LocaleMatcher supportedLSRs={de-Latn-DE, fr-Latn-FR} default=de_DE@numbers=latn threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[de_DE@numbers=latn, fr_FR@calendar=gregorian]}}"),
        Case(4, "en__PSACCENT, ar_XB", "<null>|de_DE@numbers=latn|-1|-1|de_DE@numbers=latn", "{LocaleMatcher supportedLSRs={de-Latn-DE, fr-Latn-FR} default=de_DE@numbers=latn threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[de_DE@numbers=latn, fr_FR@calendar=gregorian]}}"),
        Case(4, "und", "<null>|de_DE@numbers=latn|-1|-1|de_DE@numbers=latn", "{LocaleMatcher supportedLSRs={de-Latn-DE, fr-Latn-FR} default=de_DE@numbers=latn threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[de_DE@numbers=latn, fr_FR@calendar=gregorian]}}"),
        Case(4, "@x=abc, ar", "<null>|de_DE@numbers=latn|-1|-1|de_DE@numbers=latn", "{LocaleMatcher supportedLSRs={de-Latn-DE, fr-Latn-FR} default=de_DE@numbers=latn threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[de_DE@numbers=latn, fr_FR@calendar=gregorian]}}"),
        Case(4, "ar, sr_Latn", "<null>|de_DE@numbers=latn|-1|-1|de_DE@numbers=latn", "{LocaleMatcher supportedLSRs={de-Latn-DE, fr-Latn-FR} default=de_DE@numbers=latn threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[de_DE@numbers=latn, fr_FR@calendar=gregorian]}}"),
        Case(5, "en_AU, fr_CA", "en_AU|en|0|1|en_AU", "{LocaleMatcher supportedLSRs={'en-'-XA, en-Latn-US, +ar-+-XB, ar-Arab-EG} default=en_XA threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_XA, en, ar_XB, ar]}}"),
        Case(5, "de_CH_1901@calendar=buddhist;numbers=arab", "<null>|en_XA|-1|-1|en_XA", "{LocaleMatcher supportedLSRs={'en-'-XA, en-Latn-US, +ar-+-XB, ar-Arab-EG} default=en_XA threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_XA, en, ar_XB, ar]}}"),
        Case(5, "zh_HK, ja", "<null>|en_XA|-1|-1|en_XA", "{LocaleMatcher supportedLSRs={'en-'-XA, en-Latn-US, +ar-+-XB, ar-Arab-EG} default=en_XA threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_XA, en, ar_XB, ar]}}"),
        Case(5, "no, nn", "<null>|en_XA|-1|-1|en_XA", "{LocaleMatcher supportedLSRs={'en-'-XA, en-Latn-US, +ar-+-XB, ar-Arab-EG} default=en_XA threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_XA, en, ar_XB, ar]}}"),
        Case(5, "en__PSACCENT, ar_XB", "en__PSACCENT|en_XA|0|0|en_XA_PSACCENT", "{LocaleMatcher supportedLSRs={'en-'-XA, en-Latn-US, +ar-+-XB, ar-Arab-EG} default=en_XA threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_XA, en, ar_XB, ar]}}"),
        Case(5, "und", "<null>|en_XA|-1|-1|en_XA", "{LocaleMatcher supportedLSRs={'en-'-XA, en-Latn-US, +ar-+-XB, ar-Arab-EG} default=en_XA threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_XA, en, ar_XB, ar]}}"),
        Case(5, "@x=abc, ar", "ar|ar|1|3|ar", "{LocaleMatcher supportedLSRs={'en-'-XA, en-Latn-US, +ar-+-XB, ar-Arab-EG} default=en_XA threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_XA, en, ar_XB, ar]}}"),
        Case(5, "ar, sr_Latn", "ar|ar|0|3|ar", "{LocaleMatcher supportedLSRs={'en-'-XA, en-Latn-US, +ar-+-XB, ar-Arab-EG} default=en_XA threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_XA, en, ar_XB, ar]}}"),
        Case(6, "en_AU, fr_CA", "<null>|<null>|-1|-1|<null>", "{LocaleMatcher default=null threshold=50 demotion=5}", "{LocaleMatcher.Builder}"),
        Case(6, "de_CH_1901@calendar=buddhist;numbers=arab", "<null>|<null>|-1|-1|<null>", "{LocaleMatcher default=null threshold=50 demotion=5}", "{LocaleMatcher.Builder}"),
        Case(6, "zh_HK, ja", "<null>|<null>|-1|-1|<null>", "{LocaleMatcher default=null threshold=50 demotion=5}", "{LocaleMatcher.Builder}"),
        Case(6, "no, nn", "<null>|<null>|-1|-1|<null>", "{LocaleMatcher default=null threshold=50 demotion=5}", "{LocaleMatcher.Builder}"),
        Case(6, "en__PSACCENT, ar_XB", "<null>|<null>|-1|-1|<null>", "{LocaleMatcher default=null threshold=50 demotion=5}", "{LocaleMatcher.Builder}"),
        Case(6, "und", "<null>|<null>|-1|-1|<null>", "{LocaleMatcher default=null threshold=50 demotion=5}", "{LocaleMatcher.Builder}"),
        Case(6, "@x=abc, ar", "<null>|<null>|-1|-1|<null>", "{LocaleMatcher default=null threshold=50 demotion=5}", "{LocaleMatcher.Builder}"),
        Case(6, "ar, sr_Latn", "<null>|<null>|-1|-1|<null>", "{LocaleMatcher default=null threshold=50 demotion=5}", "{LocaleMatcher.Builder}"),
        Case(7, "en_AU, fr_CA", "<null>|fr_CA|-1|-1|fr_CA", "{LocaleMatcher default=fr_CA threshold=50 demotion=5}", "{LocaleMatcher.Builder default=fr_CA}"),
        Case(7, "de_CH_1901@calendar=buddhist;numbers=arab", "<null>|fr_CA|-1|-1|fr_CA", "{LocaleMatcher default=fr_CA threshold=50 demotion=5}", "{LocaleMatcher.Builder default=fr_CA}"),
        Case(7, "zh_HK, ja", "<null>|fr_CA|-1|-1|fr_CA", "{LocaleMatcher default=fr_CA threshold=50 demotion=5}", "{LocaleMatcher.Builder default=fr_CA}"),
        Case(7, "no, nn", "<null>|fr_CA|-1|-1|fr_CA", "{LocaleMatcher default=fr_CA threshold=50 demotion=5}", "{LocaleMatcher.Builder default=fr_CA}"),
        Case(7, "en__PSACCENT, ar_XB", "<null>|fr_CA|-1|-1|fr_CA", "{LocaleMatcher default=fr_CA threshold=50 demotion=5}", "{LocaleMatcher.Builder default=fr_CA}"),
        Case(7, "und", "<null>|fr_CA|-1|-1|fr_CA", "{LocaleMatcher default=fr_CA threshold=50 demotion=5}", "{LocaleMatcher.Builder default=fr_CA}"),
        Case(7, "@x=abc, ar", "<null>|fr_CA|-1|-1|fr_CA", "{LocaleMatcher default=fr_CA threshold=50 demotion=5}", "{LocaleMatcher.Builder default=fr_CA}"),
        Case(7, "ar, sr_Latn", "<null>|fr_CA|-1|-1|fr_CA", "{LocaleMatcher default=fr_CA threshold=50 demotion=5}", "{LocaleMatcher.Builder default=fr_CA}"),
        Case(8, "en_AU, fr_CA", "<null>|<null>|-1|-1|<null>", "{LocaleMatcher supportedLSRs={en-Latn-US, fr-Latn-FR} default=null threshold=1 demotion=5}", "{LocaleMatcher.Builder supported={[fr_FR, en_US]} threshold=1}"),
        Case(8, "de_CH_1901@calendar=buddhist;numbers=arab", "<null>|<null>|-1|-1|<null>", "{LocaleMatcher supportedLSRs={en-Latn-US, fr-Latn-FR} default=null threshold=1 demotion=5}", "{LocaleMatcher.Builder supported={[fr_FR, en_US]} threshold=1}"),
        Case(8, "zh_HK, ja", "<null>|<null>|-1|-1|<null>", "{LocaleMatcher supportedLSRs={en-Latn-US, fr-Latn-FR} default=null threshold=1 demotion=5}", "{LocaleMatcher.Builder supported={[fr_FR, en_US]} threshold=1}"),
        Case(8, "no, nn", "<null>|<null>|-1|-1|<null>", "{LocaleMatcher supportedLSRs={en-Latn-US, fr-Latn-FR} default=null threshold=1 demotion=5}", "{LocaleMatcher.Builder supported={[fr_FR, en_US]} threshold=1}"),
        Case(8, "en__PSACCENT, ar_XB", "<null>|<null>|-1|-1|<null>", "{LocaleMatcher supportedLSRs={en-Latn-US, fr-Latn-FR} default=null threshold=1 demotion=5}", "{LocaleMatcher.Builder supported={[fr_FR, en_US]} threshold=1}"),
        Case(8, "und", "<null>|<null>|-1|-1|<null>", "{LocaleMatcher supportedLSRs={en-Latn-US, fr-Latn-FR} default=null threshold=1 demotion=5}", "{LocaleMatcher.Builder supported={[fr_FR, en_US]} threshold=1}"),
        Case(8, "@x=abc, ar", "<null>|<null>|-1|-1|<null>", "{LocaleMatcher supportedLSRs={en-Latn-US, fr-Latn-FR} default=null threshold=1 demotion=5}", "{LocaleMatcher.Builder supported={[fr_FR, en_US]} threshold=1}"),
        Case(8, "ar, sr_Latn", "<null>|<null>|-1|-1|<null>", "{LocaleMatcher supportedLSRs={en-Latn-US, fr-Latn-FR} default=null threshold=1 demotion=5}", "{LocaleMatcher.Builder supported={[fr_FR, en_US]} threshold=1}"),
        Case(9, "en_AU, fr_CA", "en_AU|en_US|0|0|en_AU", "{LocaleMatcher supportedLSRs={en-Latn-US, und, de-Latn-DE} default=en_US threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_US, , de]}}"),
        Case(9, "de_CH_1901@calendar=buddhist;numbers=arab", "de_CH_1901@calendar=buddhist;numbers=arab|de|0|2|de_CH_1901@calendar=buddhist;numbers=arab", "{LocaleMatcher supportedLSRs={en-Latn-US, und, de-Latn-DE} default=en_US threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_US, , de]}}"),
        Case(9, "zh_HK, ja", "<null>|en_US|-1|-1|en_US", "{LocaleMatcher supportedLSRs={en-Latn-US, und, de-Latn-DE} default=en_US threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_US, , de]}}"),
        Case(9, "no, nn", "<null>|en_US|-1|-1|en_US", "{LocaleMatcher supportedLSRs={en-Latn-US, und, de-Latn-DE} default=en_US threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_US, , de]}}"),
        Case(9, "en__PSACCENT, ar_XB", "<null>|en_US|-1|-1|en_US", "{LocaleMatcher supportedLSRs={en-Latn-US, und, de-Latn-DE} default=en_US threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_US, , de]}}"),
        Case(9, "und", "||0|1|", "{LocaleMatcher supportedLSRs={en-Latn-US, und, de-Latn-DE} default=en_US threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_US, , de]}}"),
        Case(9, "@x=abc, ar", "<null>|en_US|-1|-1|en_US", "{LocaleMatcher supportedLSRs={en-Latn-US, und, de-Latn-DE} default=en_US threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_US, , de]}}"),
        Case(9, "ar, sr_Latn", "<null>|en_US|-1|-1|en_US", "{LocaleMatcher supportedLSRs={en-Latn-US, und, de-Latn-DE} default=en_US threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_US, , de]}}"),
        Case(10, "en_AU, fr_CA", "en_AU|en_AU|0|0|en_AU", "{LocaleMatcher supportedLSRs={en-Latn-AU, en-Latn-US, en-Latn-GB} default=en_AU threshold=6 demotion=5}", "{LocaleMatcher.Builder supported={[en_AU, en_US, en_GB]}}"),
        Case(10, "de_CH_1901@calendar=buddhist;numbers=arab", "<null>|en_AU|-1|-1|en_AU", "{LocaleMatcher supportedLSRs={en-Latn-AU, en-Latn-US, en-Latn-GB} default=en_AU threshold=6 demotion=5}", "{LocaleMatcher.Builder supported={[en_AU, en_US, en_GB]}}"),
        Case(10, "zh_HK, ja", "<null>|en_AU|-1|-1|en_AU", "{LocaleMatcher supportedLSRs={en-Latn-AU, en-Latn-US, en-Latn-GB} default=en_AU threshold=6 demotion=5}", "{LocaleMatcher.Builder supported={[en_AU, en_US, en_GB]}}"),
        Case(10, "no, nn", "<null>|en_AU|-1|-1|en_AU", "{LocaleMatcher supportedLSRs={en-Latn-AU, en-Latn-US, en-Latn-GB} default=en_AU threshold=6 demotion=5}", "{LocaleMatcher.Builder supported={[en_AU, en_US, en_GB]}}"),
        Case(10, "en__PSACCENT, ar_XB", "<null>|en_AU|-1|-1|en_AU", "{LocaleMatcher supportedLSRs={en-Latn-AU, en-Latn-US, en-Latn-GB} default=en_AU threshold=6 demotion=5}", "{LocaleMatcher.Builder supported={[en_AU, en_US, en_GB]}}"),
        Case(10, "und", "<null>|en_AU|-1|-1|en_AU", "{LocaleMatcher supportedLSRs={en-Latn-AU, en-Latn-US, en-Latn-GB} default=en_AU threshold=6 demotion=5}", "{LocaleMatcher.Builder supported={[en_AU, en_US, en_GB]}}"),
        Case(10, "@x=abc, ar", "<null>|en_AU|-1|-1|en_AU", "{LocaleMatcher supportedLSRs={en-Latn-AU, en-Latn-US, en-Latn-GB} default=en_AU threshold=6 demotion=5}", "{LocaleMatcher.Builder supported={[en_AU, en_US, en_GB]}}"),
        Case(10, "ar, sr_Latn", "<null>|en_AU|-1|-1|en_AU", "{LocaleMatcher supportedLSRs={en-Latn-AU, en-Latn-US, en-Latn-GB} default=en_AU threshold=6 demotion=5}", "{LocaleMatcher.Builder supported={[en_AU, en_US, en_GB]}}"),
        Case(11, "en_AU, fr_CA", "en_AU|en_Latn|0|0|en_Latn_AU", "{LocaleMatcher supportedLSRs={en-Latn-US, sr-Cyrl-RS, az-Arab-IR} default=en_Latn favor=SCRIPT threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_Latn, sr_Cyrl, az_Arab]} distance=SCRIPT}"),
        Case(11, "de_CH_1901@calendar=buddhist;numbers=arab", "de_CH_1901@calendar=buddhist;numbers=arab|en_Latn|0|0|en_Latn_CH_1901@calendar=buddhist;numbers=arab", "{LocaleMatcher supportedLSRs={en-Latn-US, sr-Cyrl-RS, az-Arab-IR} default=en_Latn favor=SCRIPT threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_Latn, sr_Cyrl, az_Arab]} distance=SCRIPT}"),
        Case(11, "zh_HK, ja", "<null>|en_Latn|-1|-1|en_Latn", "{LocaleMatcher supportedLSRs={en-Latn-US, sr-Cyrl-RS, az-Arab-IR} default=en_Latn favor=SCRIPT threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_Latn, sr_Cyrl, az_Arab]} distance=SCRIPT}"),
        Case(11, "no, nn", "no|en_Latn|0|0|en_Latn", "{LocaleMatcher supportedLSRs={en-Latn-US, sr-Cyrl-RS, az-Arab-IR} default=en_Latn favor=SCRIPT threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_Latn, sr_Cyrl, az_Arab]} distance=SCRIPT}"),
        Case(11, "en__PSACCENT, ar_XB", "<null>|en_Latn|-1|-1|en_Latn", "{LocaleMatcher supportedLSRs={en-Latn-US, sr-Cyrl-RS, az-Arab-IR} default=en_Latn favor=SCRIPT threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_Latn, sr_Cyrl, az_Arab]} distance=SCRIPT}"),
        Case(11, "und", "<null>|en_Latn|-1|-1|en_Latn", "{LocaleMatcher supportedLSRs={en-Latn-US, sr-Cyrl-RS, az-Arab-IR} default=en_Latn favor=SCRIPT threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_Latn, sr_Cyrl, az_Arab]} distance=SCRIPT}"),
        Case(11, "@x=abc, ar", "ar|az_Arab|1|2|az_Arab", "{LocaleMatcher supportedLSRs={en-Latn-US, sr-Cyrl-RS, az-Arab-IR} default=en_Latn favor=SCRIPT threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_Latn, sr_Cyrl, az_Arab]} distance=SCRIPT}"),
        Case(11, "ar, sr_Latn", "sr_Latn|sr_Cyrl|1|1|sr_Cyrl", "{LocaleMatcher supportedLSRs={en-Latn-US, sr-Cyrl-RS, az-Arab-IR} default=en_Latn favor=SCRIPT threshold=50 demotion=5}", "{LocaleMatcher.Builder supported={[en_Latn, sr_Cyrl, az_Arab]} distance=SCRIPT}"),
    )
    private fun builder(config: Int): LocaleMatcher.Builder = LocaleMatcher.builder().apply {
        when (config) {
            0 -> setSupportedLocales("en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN")
            1 -> setSupportedLocales("en_GB, en_US, fr_FR, de_DE, zh_Hant_TW, zh_Hans_CN").setDemotionPerDesiredLocale(LocaleMatcher.Demotion.NONE)
            2 -> setSupportedLocales("en, ru, sr_Cyrl, sr_Latn, zh_Hant, zh_Hans, ja")
            3 -> setSupportedLocales("nb, nn, da").setDirection(LocaleMatcher.Direction.ONLY_TWO_WAY)
            4 -> setSupportedLocales("de_DE@numbers=latn, fr_FR@calendar=gregorian")
            5 -> setSupportedLocales("en_XA, en, ar_XB, ar")
            6 -> setNoDefaultLocale()
            7 -> setDefaultULocale(ULocale("fr_CA"))
            8 -> setSupportedLocales("fr_FR, en_US").setNoDefaultLocale().internalSetThresholdDistance(1)
            9 -> setSupportedLocales("und, en_US, root, de")
            10 -> setSupportedLocales("en_AU, en_US, en_GB").setMaxDistance(ULocale("en_US"), ULocale("en_GB"))
            11 -> setSupportedLocales("en_Latn, sr_Cyrl, az_Arab").setFavorSubtag(LocaleMatcher.FavorSubtag.SCRIPT)
        }
    }
    private fun signature(result: LocaleMatcher.Result): String = listOf(
        result.getDesiredULocale()?.toString() ?: "<null>", result.getSupportedULocale()?.toString() ?: "<null>",
        result.getDesiredIndex(), result.getSupportedIndex(), result.makeResolvedULocale()?.toString() ?: "<null>").joinToString("|")
    @Test fun sourceResultsOrderingDefaultsFlagsAndResolutionGoldens() {
        for (case in cases) {
            val b = builder(case.config)
            assertEquals(case.builder, b.toString(), "builder ${case.config}")
            val m = b.build()
            assertEquals(case.matcher, m.toString(), "matcher ${case.config}")
            val desired = LocalePriorityList.add(case.desired).build()
            assertEquals(case.result, signature(m.getBestMatchResult(desired)), "${case.config}/${case.desired}")
            assertEquals(m.getBestMatch(desired), m.getBestMatch(case.desired))
        }
    }
    @Test fun immutableBuilderSnapshotsAndExactMatchShortCircuit() {
        val supported = ULocale("en_US")
        val inputs = mutableListOf(supported)
        val builder = LocaleMatcher.builder().setSupportedULocales(inputs)
        inputs.clear()
        val first = builder.build()
        builder.addSupportedULocale(ULocale("fr_FR")).setNoDefaultLocale()
        val second = builder.build()
        assertSame(supported, first.getBestMatch(ULocale("en")))
        assertNull(second.getBestMatch(ULocale("ja")))
        assertEquals(ULocale("fr_FR"), second.getBestMatch(ULocale("fr")))
        val desired = ULocale("en_US")
        val iterator = object : Iterator<ULocale> {
            var calls = 0
            override fun hasNext(): Boolean = true
            override fun next(): ULocale { check(calls++ == 0) { "exact match must stop iteration" }; return desired }
        }
        val result = first.getBestMatchResult(Iterable { iterator })
        assertSame(desired, result.getDesiredULocale())
        assertSame(supported, result.getSupportedULocale())
        assertEquals(0, result.getDesiredIndex())
        assertEquals(0, result.getSupportedIndex())
    }
    @Test fun nullValidationAndNoDefaultResult() {
        assertFailsWith<IllegalArgumentException> { LocaleMatcher.builder().setMaxDistance(null, ULocale.US) }
        val builder = LocaleMatcher.builder().addSupportedULocale(null)
        assertFailsWith<NullPointerException> { builder.build() }
        val empty = LocaleMatcher.builder().setNoDefaultLocale().build()
        val result = empty.getBestMatchResult(emptyList<ULocale>())
        assertNull(result.getDesiredULocale()); assertNull(result.getSupportedULocale()); assertNull(result.makeResolvedULocale())
        assertEquals(-1, result.getDesiredIndex()); assertEquals(-1, result.getSupportedIndex())
    }
    @Test fun localizedDiagnosticIntegers() {
        val previous = ULocale.getDefault()
        try {
            ULocale.setDefault(ULocale("ar_EG"))
            val builder = LocaleMatcher.builder().setSupportedLocales("en, fr").internalSetThresholdDistance(5)
            assertTrue(builder.toString().contains("threshold=٥"))
            assertTrue(builder.build().toString().endsWith("threshold=٥ demotion=٥}"))
        } finally { ULocale.setDefault(previous) }
    }

}
