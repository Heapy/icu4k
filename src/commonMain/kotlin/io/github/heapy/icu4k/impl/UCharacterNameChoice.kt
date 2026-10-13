// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

interface UCharacterNameChoice {
    companion object {
        const val UNICODE_CHAR_NAME = 0
        const val OBSOLETE_UNUSED_UNICODE_10_CHAR_NAME = 1
        const val EXTENDED_CHAR_NAME = 2
        const val CHAR_NAME_ALIAS = 3
        const val CHAR_NAME_CHOICE_COUNT = 4
        const val ISO_COMMENT_ = CHAR_NAME_CHOICE_COUNT
    }
}
