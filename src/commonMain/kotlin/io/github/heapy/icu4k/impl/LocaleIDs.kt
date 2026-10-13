// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2009-2013, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Original ICU 78.3 ISO language/region tables and obsolete-code mappings. */
object LocaleIDs {
    fun getISOCountries(): Array<String> = _countries.copyOf()
    fun getISOLanguages(): Array<String> = _languages.copyOf()
    fun getISO3Country(country: String): String = lookup(_countries, _countries3, country) ?: lookup(_obsoleteCountries, _obsoleteCountries3, country) ?: ""
    fun getISO3Language(language: String): String = lookup(_languages, _languages3, language) ?: lookup(_obsoleteLanguages, _obsoleteLanguages3, language) ?: ""
    fun threeToTwoLetterLanguage(language: String): String? = lookup(_languages3, _languages, language) ?: lookup(_obsoleteLanguages3, _obsoleteLanguages, language)
    fun threeToTwoLetterRegion(region: String): String? = lookup(_countries3, _countries, region) ?: lookup(_obsoleteCountries3, _obsoleteCountries, region)
    fun getCurrentCountryID(oldID: String): String = lookup(_deprecatedCountries, _replacementCountries, oldID) ?: oldID
    fun getCurrentLanguageID(oldID: String): String = lookup(_obsoleteLanguages, _replacementLanguages, oldID) ?: oldID
    private fun lookup(keys: Array<String>, values: Array<String>, key: String): String? {
        val index = keys.indexOf(key)
        return if (index < 0) null else values[index]
    }
    private val _languages = arrayOf(
        "aa", "ab", "ace", "ach", "ada", "ady", "ae", "aeb", "af", "afh", "agq", "ain",
        "ak", "akk", "akz", "ale", "aln", "alt", "am", "an", "ang", "anp", "ar", "arc",
        "arn", "aro", "arp", "arq", "ars", "arw", "ary", "arz", "as", "asa", "ase", "ast",
        "av", "avk", "awa", "ay", "az", "ba", "bal", "ban", "bar", "bas", "bax", "bbc",
        "bbj", "be", "bej", "bem", "bew", "bez", "bfd", "bfq", "bg", "bgc", "bgn", "bho",
        "bi", "bik", "bin", "bjn", "bkm", "bla", "bm", "bn", "bo", "bpy", "bqi", "br",
        "bra", "brh", "brx", "bs", "bss", "bua", "bug", "bum", "byn", "byv", "ca", "cad",
        "car", "cay", "cch", "ccp", "ce", "ceb", "cgg", "ch", "chb", "chg", "chk", "chm",
        "chn", "cho", "chp", "chr", "chy", "ckb", "co", "cop", "cps", "cr", "crh", "cs",
        "csb", "cu", "cv", "cy", "da", "dak", "dar", "dav", "day", "de", "del", "den",
        "dgr", "din", "dje", "doi", "dra", "dsb", "dua", "dum", "dv", "dyo", "dyu", "dz",
        "dzg", "ebu", "ee", "efi", "egy", "eka", "el", "elx", "en", "enm", "eo", "es",
        "et", "eu", "ewo", "fa", "fan", "fat", "ff", "fi", "fil", "fiu", "fj", "fo",
        "fon", "fr", "frm", "fro", "frr", "frs", "fur", "fy", "ga", "gaa", "gay", "gba",
        "gd", "gem", "gez", "gil", "gl", "gmh", "gn", "goh", "gon", "gor", "got", "grb",
        "grc", "gsw", "gu", "guz", "gv", "gwi", "ha", "hai", "haw", "he", "hi", "hil",
        "him", "hit", "hmn", "ho", "hr", "hsb", "ht", "hu", "hup", "hy", "hz", "ia",
        "iba", "ibb", "id", "ie", "ig", "ii", "ijo", "ik", "ilo", "inc", "ine", "inh",
        "io", "ira", "iro", "is", "it", "iu", "ja", "jbo", "jgo", "jmc", "jpr", "jrb",
        "jv", "ka", "kaa", "kab", "kac", "kaj", "kam", "kar", "kaw", "kbd", "kbl", "kcg",
        "kde", "kea", "kfo", "kg", "kha", "khi", "kho", "khq", "ki", "kj", "kk", "kkj",
        "kl", "kln", "km", "kmb", "kn", "ko", "kok", "kos", "kpe", "kr", "krc", "krl",
        "kro", "kru", "ks", "ksb", "ksf", "ksh", "ku", "kum", "kut", "kv", "kw", "ky",
        "la", "lad", "lag", "lah", "lam", "lb", "lez", "lg", "li", "lkt", "ln", "lo",
        "lol", "loz", "lt", "lu", "lua", "lui", "lun", "luo", "lus", "luy", "lv", "mad",
        "maf", "mag", "mai", "mak", "man", "map", "mas", "mde", "mdf", "mdr", "men", "mer",
        "mfe", "mg", "mga", "mgh", "mgo", "mh", "mi", "mic", "min", "mis", "mk", "mkh",
        "ml", "mn", "mnc", "mni", "mno", "moh", "mos", "mr", "ms", "mt", "mua", "mul",
        "mun", "mus", "mwl", "mwr", "my", "mye", "myn", "myv", "na", "nah", "nai", "nap",
        "naq", "nb", "nd", "nds", "ne", "new", "ng", "nia", "nic", "niu", "nl", "nmg",
        "nn", "nnh", "no", "nog", "non", "nqo", "nr", "nso", "nub", "nus", "nv", "nwc",
        "ny", "nym", "nyn", "nyo", "nzi", "oc", "oj", "om", "or", "os", "osa", "ota",
        "oto", "pa", "paa", "pag", "pal", "pam", "pap", "pau", "peo", "phi", "phn", "pi",
        "pl", "pon", "pra", "pro", "ps", "pt", "qu", "raj", "rap", "rar", "rm", "rn",
        "ro", "roa", "rof", "rom", "ru", "rup", "rw", "rwk", "sa", "sad", "sah", "sai",
        "sal", "sam", "saq", "sas", "sat", "sba", "sbp", "sc", "scn", "sco", "sd", "se",
        "see", "seh", "sel", "sem", "ses", "sg", "sga", "sgn", "shi", "shn", "shu", "si",
        "sid", "sio", "sit", "sk", "sl", "sla", "sm", "sma", "smi", "smj", "smn", "sms",
        "sn", "snk", "so", "sog", "son", "sq", "sr", "srn", "srr", "ss", "ssa", "ssy",
        "st", "su", "suk", "sus", "sux", "sv", "sw", "swb", "syc", "syr", "ta", "tai",
        "te", "tem", "teo", "ter", "tet", "tg", "th", "ti", "tig", "tiv", "tk", "tkl",
        "tlh", "tli", "tmh", "tn", "to", "tog", "tpi", "tr", "trv", "ts", "tsi", "tt",
        "tum", "tup", "tut", "tvl", "tw", "twq", "ty", "tyv", "tzm", "udm", "ug", "uga",
        "uk", "umb", "und", "ur", "uz", "vai", "ve", "vi", "vo", "vot", "vun", "wa",
        "wae", "wak", "wal", "war", "was", "wen", "wo", "xal", "xh", "xog", "yao", "yap",
        "yav", "ybb", "yi", "yo", "ypk", "yue", "za", "zap", "zbl", "zen", "zh", "znd",
        "zu", "zun", "zxx", "zza",
    )
    private val _replacementLanguages = arrayOf(
        "id", "he", "yi", "jv", "ro", "sr",
    )
    private val _obsoleteLanguages = arrayOf(
        "in", "iw", "ji", "jw", "mo", "sh",
    )
    private val _languages3 = arrayOf(
        "aar", "abk", "ace", "ach", "ada", "ady", "ave", "aeb", "afr", "afh", "agq", "ain",
        "aka", "akk", "akz", "ale", "aln", "alt", "amh", "arg", "ang", "anp", "ara", "arc",
        "arn", "aro", "arp", "arq", "ars", "arw", "ary", "arz", "asm", "asa", "ase", "ast",
        "ava", "avk", "awa", "aym", "aze", "bak", "bal", "ban", "bar", "bas", "bax", "bbc",
        "bbj", "bel", "bej", "bem", "bew", "bez", "bfd", "bfq", "bul", "bgc", "bgn", "bho",
        "bis", "bik", "bin", "bjn", "bkm", "bla", "bam", "ben", "bod", "bpy", "bqi", "bre",
        "bra", "brh", "brx", "bos", "bss", "bua", "bug", "bum", "byn", "byv", "cat", "cad",
        "car", "cay", "cch", "ccp", "che", "ceb", "cgg", "cha", "chb", "chg", "chk", "chm",
        "chn", "cho", "chp", "chr", "chy", "ckb", "cos", "cop", "cps", "cre", "crh", "ces",
        "csb", "chu", "chv", "cym", "dan", "dak", "dar", "dav", "day", "deu", "del", "den",
        "dgr", "din", "dje", "doi", "dra", "dsb", "dua", "dum", "div", "dyo", "dyu", "dzo",
        "dzg", "ebu", "ewe", "efi", "egy", "eka", "ell", "elx", "eng", "enm", "epo", "spa",
        "est", "eus", "ewo", "fas", "fan", "fat", "ful", "fin", "fil", "fiu", "fij", "fao",
        "fon", "fra", "frm", "fro", "frr", "frs", "fur", "fry", "gle", "gaa", "gay", "gba",
        "gla", "gem", "gez", "gil", "glg", "gmh", "grn", "goh", "gon", "gor", "got", "grb",
        "grc", "gsw", "guj", "guz", "glv", "gwi", "hau", "hai", "haw", "heb", "hin", "hil",
        "him", "hit", "hmn", "hmo", "hrv", "hsb", "hat", "hun", "hup", "hye", "her", "ina",
        "iba", "ibb", "ind", "ile", "ibo", "iii", "ijo", "ipk", "ilo", "inc", "ine", "inh",
        "ido", "ira", "iro", "isl", "ita", "iku", "jpn", "jbo", "jgo", "jmc", "jpr", "jrb",
        "jav", "kat", "kaa", "kab", "kac", "kaj", "kam", "kar", "kaw", "kbd", "kbl", "kcg",
        "kde", "kea", "kfo", "kon", "kha", "khi", "kho", "khq", "kik", "kua", "kaz", "kkj",
        "kal", "kln", "khm", "kmb", "kan", "kor", "kok", "kos", "kpe", "kau", "krc", "krl",
        "kro", "kru", "kas", "ksb", "ksf", "ksh", "kur", "kum", "kut", "kom", "cor", "kir",
        "lat", "lad", "lag", "lah", "lam", "ltz", "lez", "lug", "lim", "lkt", "lin", "lao",
        "lol", "loz", "lit", "lub", "lua", "lui", "lun", "luo", "lus", "luy", "lav", "mad",
        "maf", "mag", "mai", "mak", "man", "map", "mas", "mde", "mdf", "mdr", "men", "mer",
        "mfe", "mlg", "mga", "mgh", "mgo", "mah", "mri", "mic", "min", "mis", "mkd", "mkh",
        "mal", "mon", "mnc", "mni", "mno", "moh", "mos", "mar", "msa", "mlt", "mua", "mul",
        "mun", "mus", "mwl", "mwr", "mya", "mye", "myn", "myv", "nau", "nah", "nai", "nap",
        "naq", "nob", "nde", "nds", "nep", "new", "ndo", "nia", "nic", "niu", "nld", "nmg",
        "nno", "nnh", "nor", "nog", "non", "nqo", "nbl", "nso", "nub", "nus", "nav", "nwc",
        "nya", "nym", "nyn", "nyo", "nzi", "oci", "oji", "orm", "ori", "oss", "osa", "ota",
        "oto", "pan", "paa", "pag", "pal", "pam", "pap", "pau", "peo", "phi", "phn", "pli",
        "pol", "pon", "pra", "pro", "pus", "por", "que", "raj", "rap", "rar", "roh", "run",
        "ron", "roa", "rof", "rom", "rus", "rup", "kin", "rwk", "san", "sad", "sah", "sai",
        "sal", "sam", "saq", "sas", "sat", "sba", "sbp", "srd", "scn", "sco", "snd", "sme",
        "see", "seh", "sel", "sem", "ses", "sag", "sga", "sgn", "shi", "shn", "shu", "sin",
        "sid", "sio", "sit", "slk", "slv", "sla", "smo", "sma", "smi", "smj", "smn", "sms",
        "sna", "snk", "som", "sog", "son", "sqi", "srp", "srn", "srr", "ssw", "ssa", "ssy",
        "sot", "sun", "suk", "sus", "sux", "swe", "swa", "swb", "syc", "syr", "tam", "tai",
        "tel", "tem", "teo", "ter", "tet", "tgk", "tha", "tir", "tig", "tiv", "tuk", "tkl",
        "tlh", "tli", "tmh", "tsn", "ton", "tog", "tpi", "tur", "trv", "tso", "tsi", "tat",
        "tum", "tup", "tut", "tvl", "twi", "twq", "tah", "tyv", "tzm", "udm", "uig", "uga",
        "ukr", "umb", "und", "urd", "uzb", "vai", "ven", "vie", "vol", "vot", "vun", "wln",
        "wae", "wak", "wal", "war", "was", "wen", "wol", "xal", "xho", "xog", "yao", "yap",
        "yav", "ybb", "yid", "yor", "ypk", "yue", "zha", "zap", "zbl", "zen", "zho", "znd",
        "zul", "zun", "zxx", "zza",
    )
    private val _obsoleteLanguages3 = arrayOf(
        "ind", "heb", "yid", "jaw", "ron", "srp",
    )
    private val _countries = arrayOf(
        "AD", "AE", "AF", "AG", "AI", "AL", "AM", "AO", "AQ", "AR", "AS", "AT",
        "AU", "AW", "AX", "AZ", "BA", "BB", "BD", "BE", "BF", "BG", "BH", "BI",
        "BJ", "BL", "BM", "BN", "BO", "BQ", "BR", "BS", "BT", "BV", "BW", "BY",
        "BZ", "CA", "CC", "CD", "CF", "CG", "CH", "CI", "CK", "CL", "CM", "CN",
        "CO", "CR", "CU", "CV", "CW", "CX", "CY", "CZ", "DE", "DG", "DJ", "DK",
        "DM", "DO", "DZ", "EA", "EC", "EE", "EG", "EH", "ER", "ES", "ET", "FI",
        "FJ", "FK", "FM", "FO", "FR", "GA", "GB", "GD", "GE", "GF", "GG", "GH",
        "GI", "GL", "GM", "GN", "GP", "GQ", "GR", "GS", "GT", "GU", "GW", "GY",
        "HK", "HM", "HN", "HR", "HT", "HU", "IC", "ID", "IE", "IL", "IM", "IN",
        "IO", "IQ", "IR", "IS", "IT", "JE", "JM", "JO", "JP", "KE", "KG", "KH",
        "KI", "KM", "KN", "KP", "KR", "KW", "KY", "KZ", "LA", "LB", "LC", "LI",
        "LK", "LR", "LS", "LT", "LU", "LV", "LY", "MA", "MC", "MD", "ME", "MF",
        "MG", "MH", "MK", "ML", "MM", "MN", "MO", "MP", "MQ", "MR", "MS", "MT",
        "MU", "MV", "MW", "MX", "MY", "MZ", "NA", "NC", "NE", "NF", "NG", "NI",
        "NL", "NO", "NP", "NR", "NU", "NZ", "OM", "PA", "PE", "PF", "PG", "PH",
        "PK", "PL", "PM", "PN", "PR", "PS", "PT", "PW", "PY", "QA", "RE", "RO",
        "RS", "RU", "RW", "SA", "SB", "SC", "SD", "SE", "SG", "SH", "SI", "SJ",
        "SK", "SL", "SM", "SN", "SO", "SR", "SS", "ST", "SV", "SX", "SY", "SZ",
        "TC", "TD", "TF", "TG", "TH", "TJ", "TK", "TL", "TM", "TN", "TO", "TR",
        "TT", "TV", "TW", "TZ", "UA", "UG", "UM", "US", "UY", "UZ", "VA", "VC",
        "VE", "VG", "VI", "VN", "VU", "WF", "WS", "XK", "YE", "YT", "ZA", "ZM",
        "ZW",
    )
    private val _deprecatedCountries = arrayOf(
        "AN", "BU", "CS", "DD", "DY", "FX", "HV", "NH", "RH", "SU", "TP", "UK",
        "VD", "YD", "YU", "ZR",
    )
    private val _replacementCountries = arrayOf(
        "CW", "MM", "RS", "DE", "BJ", "FR", "BF", "VU", "ZW", "RU", "TL", "GB",
        "VN", "YE", "RS", "CD",
    )
    private val _obsoleteCountries = arrayOf(
        "AN", "BU", "CS", "FX", "RO", "SU", "TP", "YD", "YU", "ZR",
    )
    private val _countries3 = arrayOf(
        "AND", "ARE", "AFG", "ATG", "AIA", "ALB", "ARM", "AGO", "ATA", "ARG", "ASM", "AUT",
        "AUS", "ABW", "ALA", "AZE", "BIH", "BRB", "BGD", "BEL", "BFA", "BGR", "BHR", "BDI",
        "BEN", "BLM", "BMU", "BRN", "BOL", "BES", "BRA", "BHS", "BTN", "BVT", "BWA", "BLR",
        "BLZ", "CAN", "CCK", "COD", "CAF", "COG", "CHE", "CIV", "COK", "CHL", "CMR", "CHN",
        "COL", "CRI", "CUB", "CPV", "CUW", "CXR", "CYP", "CZE", "DEU", "DGA", "DJI", "DNK",
        "DMA", "DOM", "DZA", "XEA", "ECU", "EST", "EGY", "ESH", "ERI", "ESP", "ETH", "FIN",
        "FJI", "FLK", "FSM", "FRO", "FRA", "GAB", "GBR", "GRD", "GEO", "GUF", "GGY", "GHA",
        "GIB", "GRL", "GMB", "GIN", "GLP", "GNQ", "GRC", "SGS", "GTM", "GUM", "GNB", "GUY",
        "HKG", "HMD", "HND", "HRV", "HTI", "HUN", "XIC", "IDN", "IRL", "ISR", "IMN", "IND",
        "IOT", "IRQ", "IRN", "ISL", "ITA", "JEY", "JAM", "JOR", "JPN", "KEN", "KGZ", "KHM",
        "KIR", "COM", "KNA", "PRK", "KOR", "KWT", "CYM", "KAZ", "LAO", "LBN", "LCA", "LIE",
        "LKA", "LBR", "LSO", "LTU", "LUX", "LVA", "LBY", "MAR", "MCO", "MDA", "MNE", "MAF",
        "MDG", "MHL", "MKD", "MLI", "MMR", "MNG", "MAC", "MNP", "MTQ", "MRT", "MSR", "MLT",
        "MUS", "MDV", "MWI", "MEX", "MYS", "MOZ", "NAM", "NCL", "NER", "NFK", "NGA", "NIC",
        "NLD", "NOR", "NPL", "NRU", "NIU", "NZL", "OMN", "PAN", "PER", "PYF", "PNG", "PHL",
        "PAK", "POL", "SPM", "PCN", "PRI", "PSE", "PRT", "PLW", "PRY", "QAT", "REU", "ROU",
        "SRB", "RUS", "RWA", "SAU", "SLB", "SYC", "SDN", "SWE", "SGP", "SHN", "SVN", "SJM",
        "SVK", "SLE", "SMR", "SEN", "SOM", "SUR", "SSD", "STP", "SLV", "SXM", "SYR", "SWZ",
        "TCA", "TCD", "ATF", "TGO", "THA", "TJK", "TKL", "TLS", "TKM", "TUN", "TON", "TUR",
        "TTO", "TUV", "TWN", "TZA", "UKR", "UGA", "UMI", "USA", "URY", "UZB", "VAT", "VCT",
        "VEN", "VGB", "VIR", "VNM", "VUT", "WLF", "WSM", "XKK", "YEM", "MYT", "ZAF", "ZMB",
        "ZWE",
    )
    private val _obsoleteCountries3 = arrayOf(
        "ANT", "BUR", "SCG", "FXX", "ROM", "SUN", "TMP", "YMD", "YUG", "ZAR",
    )
}
