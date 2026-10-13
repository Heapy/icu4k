// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

internal object RBBIRuleCompilerError {
    const val U_ILLEGAL_CHAR_FOUND = 12
    const val U_BRK_ERROR_START = 0x10200
    const val U_BRK_INTERNAL_ERROR = 0x10201
    const val U_BRK_HEX_DIGITS_EXPECTED = 0x10202
    const val U_BRK_SEMICOLON_EXPECTED = 0x10203
    const val U_BRK_RULE_SYNTAX = 0x10204
    const val U_BRK_UNCLOSED_SET = 0x10205
    const val U_BRK_ASSIGN_ERROR = 0x10206
    const val U_BRK_VARIABLE_REDFINITION = 0x10207
    const val U_BRK_MISMATCHED_PAREN = 0x10208
    const val U_BRK_NEW_LINE_IN_QUOTED_STRING = 0x10209
    const val U_BRK_UNDEFINED_VARIABLE = 0x1020a
    const val U_BRK_INIT_ERROR = 0x1020b
    const val U_BRK_RULE_EMPTY_SET = 0x1020c
    const val U_BRK_UNRECOGNIZED_OPTION = 0x1020d
    const val U_BRK_MALFORMED_RULE_TAG = 0x1020e
    const val U_BRK_MALFORMED_SET = 0x1020f
    const val U_BRK_ERROR_LIMIT = 0x10210
}
