package io.github.heapy.icu4k.impl

import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.PropertyResourceBundle
import kotlin.random.Random
import kotlin.test.*

class JavaPropertiesBundleOracleTest {
    private fun outcome(block: () -> Map<String, Any>): Any = try { block() } catch (failure: Exception) {
        if (failure is CharacterCodingException) "coding" else failure::class.simpleName + ":" + failure.message
    }
    private fun map(bundle: java.util.ResourceBundle): Map<String, Any> = bundle.keys.toList().associateWith { bundle.getObject(it) }
    private fun map(bundle: JavaResourceBundle): Map<String, Any> {
        val keys = bundle.getKeys(); val result = HashMap<String, Any>()
        while (keys.hasMoreElements()) keys.nextElement().let { result[it] = bundle.getObject(it)!! }
        return result
    }
    @Test fun syntaxIncludingEofContinuationAndMalformedEscapesMatchesJdk() {
        val corpus = mutableListOf("", "a=b", "a b", "a:b", "=v", ":v", "#comment\\\nfoo=bar", "!comment\rfoo=bar",
            "\\", "\\\n", "\\\r", "\\\r\n", "\\\n\nfoo", "\\\n#comment\nfoo", "a=\\\n#value\n",
            "a=left\\\r\n \t right", "a\\ b=value\\t\\n", "a\\=b=first\na\\=b:second", "a=\\uD83D\\uDE00",
            "a=\\u0000", "a=\\u12", "a=\\uZZZZ", "a=\\uu1234", "a=\\q", "a=b\\", "a=b\\\\", "a=b\\\\\\",
            "\uFEFFkey=value", "\u000bkey=value", "a=b\r\n\nfoo=bar")
        val random = Random(7834); val alphabet = "abc =:\\#!\t\u000c\r\n0123uAfé"
        repeat(3000) { corpus.add(buildString { repeat(random.nextInt(0, 120)) { append(alphabet[random.nextInt(alphabet.length)]) } }) }
        for ((i, text) in corpus.withIndex()) assertEquals(outcome { map(PropertyResourceBundle(StringReader(text))) },
            outcome { JavaPropertiesBundle.parse(text) }, "case $i: ${text.replace("\n", "<LF>").replace("\r", "<CR>")}")
    }
    @Test fun utf8Latin1FallbackAndForcedDecodersMatchJdk() {
        val bytes = listOf("café=é\nemoji=😀\n".toByteArray(), byteArrayOf(97, 61, -1), byteArrayOf(97, 61, -61, 40),
            byteArrayOf(97, 61, -128), byteArrayOf(97, 61, -64, -81), "\uFEFFa=b".toByteArray())
        for (data in bytes) {
            assertEquals(outcome { map(PropertyResourceBundle(data.inputStream())) }, outcome { map(JavaPropertiesBundle.read(data, null)) })
            for (encoding in listOf("ISO-8859-1", "UTF-8")) {
                assertEquals(outcome {
                    val charset = if (encoding == "UTF-8") StandardCharsets.UTF_8 else StandardCharsets.ISO_8859_1
                    val text = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString()
                    map(PropertyResourceBundle(StringReader(text)))
                }, outcome { map(JavaPropertiesBundle.read(data, encoding)) })
            }
        }
    }
}
