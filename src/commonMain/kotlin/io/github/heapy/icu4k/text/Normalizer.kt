// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Legacy normalization API being ported. Quick-check tokens are shared with [Normalizer2]. */
class Normalizer private constructor() {
    class QuickCheckResult private constructor() {
        companion object {
            internal val no = QuickCheckResult()
            internal val yes = QuickCheckResult()
            internal val maybe = QuickCheckResult()
        }
    }
    companion object {
        val NO: QuickCheckResult = QuickCheckResult.no
        val YES: QuickCheckResult = QuickCheckResult.yes
        val MAYBE: QuickCheckResult = QuickCheckResult.maybe
    }
}
