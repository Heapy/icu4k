// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

/** Sign policy shared with the number formatter; its full facade is still being ported. */
enum class SignDisplay {
    AUTO, ALWAYS, NEVER, ACCOUNTING, ACCOUNTING_ALWAYS, EXCEPT_ZERO,
    ACCOUNTING_EXCEPT_ZERO, NEGATIVE, ACCOUNTING_NEGATIVE
}
