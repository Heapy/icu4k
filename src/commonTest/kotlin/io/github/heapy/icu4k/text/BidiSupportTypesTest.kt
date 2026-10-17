// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class BidiSupportTypesTest {
    @Test fun runsRetainRawBoundsLevelParityAndCopyAllFields() {
        val run = BidiRun(4, 9, 3)
        run.insertRemove = -2
        assertEquals(4, run.getStart()); assertEquals(9, run.getLimit()); assertEquals(5, run.getLength())
        assertEquals(3.toByte(), run.getEmbeddingLevel()); assertTrue(run.isOddRun()); assertFalse(run.isEvenRun())
        assertEquals(1.toByte(), run.getDirection()); assertEquals("BidiRun 4 - 9 @ 3", run.toString())
        val copy = BidiRun().also { it.copyFrom(run) }
        assertEquals(run.toString(), copy.toString()); assertEquals(-2, copy.insertRemove)
        run.start = 1
        assertEquals(4, copy.getStart())
    }
    @Test fun classifierContextAndDefaultSentinelRemainVirtual() {
        val context = mutableMapOf(65 to 1)
        val classifier = object : BidiClassifier(context) {
            override fun classify(c: Int): Int = (getContext() as? Map<*, *>)?.get(c) as Int? ?: super.classify(c)
        }
        assertSame(context, classifier.getContext())
        assertEquals(1, classifier.classify(65)); assertEquals(23, classifier.classify(66))
        classifier.setContext(null)
        assertNull(classifier.getContext()); assertEquals(23, classifier.classify(65))
        assertEquals(23, classifier.classify(-1)); assertEquals(23, classifier.classify(Int.MAX_VALUE))
    }
}
