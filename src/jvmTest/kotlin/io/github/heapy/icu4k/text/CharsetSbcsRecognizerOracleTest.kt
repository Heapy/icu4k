package io.github.heapy.icu4k.text

import com.ibm.icu.text.CharsetDetector as SourceDetector
import com.ibm.icu.text.CharsetMatch as SourceMatch
import kotlin.random.Random
import kotlin.test.*

class CharsetSbcsRecognizerOracleTest {
    private val names = listOf("8859_1", "8859_2", "8859_5_ru", "8859_6_ar", "8859_7_el", "8859_8_I_he", "8859_8_he", "8859_9_tr", "windows_1251", "windows_1256", "KOI8_R", "IBM424_he_rtl", "IBM424_he_ltr", "IBM420_ar_rtl", "IBM420_ar_ltr")
    private val ports = CharsetSbcsTables.recognizers()
    private val sources = names.map { Class.forName("com.ibm.icu.text.CharsetRecog_sbcs\$CharsetRecog_$it").getDeclaredConstructor().apply { isAccessible = true }.newInstance() }
    private val match = Class.forName("com.ibm.icu.text.CharsetRecognizer").getDeclaredMethod("match", SourceDetector::class.java).apply { isAccessible = true }
    private val filtered = SourceDetector::class.java.getDeclaredField("fInputBytes").apply { isAccessible = true }
    private val length = SourceDetector::class.java.getDeclaredField("fInputLen").apply { isAccessible = true }
    private val c1 = SourceDetector::class.java.getDeclaredField("fC1Bytes").apply { isAccessible = true }
    private fun compare(raw: ByteArray, input: ByteArray, count: Int = input.size, flag: Boolean = false) {
        val detector = SourceDetector().setText(raw)
        filtered.set(detector, input); length.setInt(detector, count); c1.setBoolean(detector, flag)
        for (i in ports.indices) {
            val result = match.invoke(sources[i], detector) as SourceMatch?
            assertEquals(result?.let { CharsetCandidate(it.name, it.confidence, it.language) }, ports[i].match(CharsetDetectionInput(raw, filtered = input, filteredLength = count, c1Bytes = flag)), "${names[i]} count=$count c1=$flag")
        }
    }
    @Test fun malformedBytesFilteredViewsAndC1Aliases() {
        val random = Random(424420)
        repeat(1800) {
            val raw = ByteArray(random.nextInt(100)) { random.nextInt(256).toByte() }
            val input = ByteArray(random.nextInt(200)) { random.nextInt(256).toByte() }
            compare(raw, input, random.nextInt(input.size + 1), it % 2 == 0)
        }
        val all = ByteArray(256) { it.toByte() }
        for (n in 0..256) compare(byteArrayOf(), all, n, n % 2 == 0)
        for (text in listOf("the quick brown fox and the other people in the world ", "dette er en tekst som viser alle de andre ", "das ist eine lange geschichte und eine andere ", "это русский текст для проверки определения кодировки ", "هذا نص عربي لاختبار اللغة العربية ", "זה טקסט בעברית לבדיקת השפה ", "αυτό είναι ένα κείμενο για την ελληνική γλώσσα ", "bu bir türkçe metin ve diğer kelimeler ")) {
            for (encoding in ports.map { it.getName().substringBefore('_') }.distinct()) {
                val bytes = text.repeat(10).toByteArray(java.nio.charset.Charset.forName(if (encoding == "ISO-8859-8-I") "ISO-8859-8" else encoding))
                for (flag in listOf(false, true)) compare(byteArrayOf(0), bytes, flag = flag)
            }
        }
    }
    @Test fun everyLanguageModelAndNormalizationTable() {
        assertEquals(15, ports.size)
        assertEquals(27, ports.sumOf { it.models.size })
        for (port in ports) for ((_, model) in port.models) {
            val map = model.flatMap { listOf((it shr 16).toByte(), (it shr 8).toByte(), it.toByte()) }.toByteArray()
            for (flag in listOf(false, true)) {
                compare(byteArrayOf(), map, flag = flag)
                compare(map, map + byteArrayOf(0, 0x40, 0xb2.toByte(), 0xb3.toByte(), 0xb4.toByte(), 0xb5.toByte(), 0xb8.toByte(), 0xb9.toByte()), flag = flag)
            }
        }
    }
}
