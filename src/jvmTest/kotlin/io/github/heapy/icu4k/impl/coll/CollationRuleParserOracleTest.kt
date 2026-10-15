// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import com.ibm.icu.impl.coll.RuleParserOracle
import com.ibm.icu.impl.coll.CollationRuleParser as Source
import kotlin.random.Random
import kotlin.test.*

class CollationRuleParserOracleTest {
    private fun field(a: Any,name: String): Any? = a.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(a)
    private fun error(t: Throwable?): Any? {
        if(t==null) return null
        val message=if(t is NullPointerException || t is IndexOutOfBoundsException) null else if(t.cause is NullPointerException) t.message?.replace(t.cause!!.message ?: "null","<null>") else t.message
        return listOf(t.javaClass.simpleName,message,if(t is java.text.ParseException) t.errorOffset else null,error(t.cause))
    }
    private fun outcome(block: () -> Unit): Any? = try { block();null } catch(e: Throwable) { error(e) }
    private fun compare(rules: String?,failAt: Int=-1,imports: Map<String,String?>?=null,withSink: Boolean=true,dataImports: Boolean=false) {
        val sourceImporter=if(dataImports) RuleParserOracle.Imports { locale,type -> RuleParserOracle.loadRules(locale,type) } else imports?.let { map -> RuleParserOracle.Imports { locale,type -> if("$locale/$type" in map) map["$locale/$type"] else throw IllegalArgumentException("import") } }
        val a=RuleParserOracle(com.ibm.icu.impl.coll.CollationRoot.getData(),failAt,withSink,sourceImporter)
        val calls=mutableListOf<String>();val sink=RuleTestSink(calls,failAt);val b=CollationRuleParser(CollationRoot.getData());if(withSink) b.setSink(sink)
        if(dataImports || imports!=null) b.setImporter { locale,type ->
            calls.add("import|$locale|$type")
            if(dataImports) CollationLoader.loadRules(io.github.heapy.icu4k.util.ULocale(locale),type) else if("$locale/$type" in imports!!) imports["$locale/$type"] else throw IllegalArgumentException("import")
        }
        val settings=CollationSettings()
        assertEquals(outcome { a.parse(rules) },outcome { b.parse(rules,settings) },"rules=$rules/fail=$failAt")
        assertEquals(a.calls,calls,"callbacks/$rules");assertEquals(a.retained.map { it.toString() },sink.retained.map { it.toString() },"retained/$rules")
        for(name in listOf("ruleIndex","rules")) assertEquals(field(a.parser,name),field(b,name),name)
        assertEquals(field(a.parser,"rawBuilder").toString(),field(b,"rawBuilder").toString())
        assertEquals(a.settings.options,settings.options);assertEquals(a.settings.variableTop,settings.variableTop)
        for(name in listOf("reorderCodes","reorderTable","reorderRanges")) {
            val expected=field(a.settings,name);val actual=field(settings,name)
            when(expected) { is IntArray -> assertContentEquals(expected,actual as IntArray);is ByteArray -> assertContentEquals(expected,actual as ByteArray);is LongArray -> assertContentEquals(expected,actual as LongArray);else -> assertEquals(expected,actual) }
        }
    }
    @Test fun syntaxSettingsImportsAndCallbackFailures() {
        val rules=listOf<String?>(null,""," # comment\n!@","&a < b << c <<< d <<<< e = f;g,h","&[before 1]a < b << c","&[before 2]a < b","&[before 2]a << b < c","&a <* b-df =* xyz","&a <* z-a","&a <* a-b-c","&a <* é","&a < '' < 'x''y' < \\&","&a < x|b / c","&a < a|\u0301","&a < 'unterminated","&a < \\","&a # c\n <b","&a","&a < ","[strength 1][strength I][alternate shifted][maxVariable currency][caseFirst upper][caseLevel on][normalization on][numericOrdering on][backwards 2]","[reorder Latn Grek space others] [reorder]","[reorder bogus]","[reorder Latn Latn]","[hiraganaQ on]","[hiraganaQ off]","[strength 0]","[optimize [a-z]][suppressContractions [ch]]","[optimize [[a-z]&[c-f]]]","[optimize [z-a]]","[optimize [a-z]","[import de-u-co-phonebk] &z < x","[import invalid_]","&a < "+charArrayOf(0xd800.toChar()).concatToString(),"&a < \ufffe")+
            listOf("first tertiary ignorable","last tertiary ignorable","first secondary ignorable","last secondary ignorable","first primary ignorable","last primary ignorable","first variable","last variable","first regular","last regular","first implicit","last implicit","first trailing","last trailing","top","variable top","invalid").map { "&[$it] < a" }
        val imports=mapOf("de/phonebook" to "&a < ä","fr/standard" to "[import de-u-co-phonebk]&b<c")
        for(rule in rules) {
            compare(rule);compare(rule,imports=imports)
            for(fail in 1..3) compare(rule,fail,imports)
        }
        for(rule in listOf("&a<b","&a<*b-e","[optimize [a-z]]","[suppressContractions [a-z]]")) compare(rule,withSink=false)
        for(imported in listOf("?","&a <","[strength 1]&", "&a<b")) compare("&a<b [import de] &c<d",imports=mapOf("de/standard" to imported))
        compare("[import fr]",imports=imports)
    }
    @Test fun uniqueUpstreamLocaleRuleStrings() {
        val index=com.ibm.icu.util.UResourceBundle.getBundleInstance(com.ibm.icu.impl.ICUData.ICU_COLLATION_BASE_NAME,"res_index")
        val rules=linkedSetOf<String>()
        for(locale in index.get("InstalledLocales").keySet()) for(type in listOf("standard","search","phonebook","traditional","stroke")) {
            val out=com.ibm.icu.util.Output<com.ibm.icu.util.ULocale>()
            rules.add(com.ibm.icu.impl.coll.CollationLoader.loadTailoring(com.ibm.icu.util.ULocale("$locale@collation=$type"),out).rules)
        }
        for(rule in rules) compare(rule,dataImports=true)
        compare("[import de]",imports=mapOf("de/standard" to null))
    }
    @Test fun randomizedRuleFragmentsAndErrorContexts() {
        val random=Random(78320)
        val fragments=listOf("&a","< b","<< c","<<<d","= e","<* f-h","[strength 2]","[caseLevel on]","[reorder Grek Latn]","[optimize [a-c]]","# comment\u2028","'x y'","\\'","|","/","!","@","["," ]","😀","\u0301"," ","\uffff")
        repeat(1200) { compare(buildString { repeat(random.nextInt(1,12)) { append(fragments.random(random)) } }) }
        for(n in 0..40) compare("&'"+"😀".repeat(n)+"' < ")
        for(word in listOf("Latn","Latin","Grek","Greek","space","PUNCT","others","Zzzz","currency","digit","not a script","")) assertEquals(Source.getReorderCode(word),CollationRuleParser.getReorderCode(word))
    }
}
