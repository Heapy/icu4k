// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2001-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

internal object BidiImplicitTables {
    const val _L = 0
    const val _R = 1
    const val _EN = 2
    const val _AN = 3
    const val _ON = 4
    const val _S = 5
    const val _B = 6
    const val IMPTABPROPS_COLUMNS = 16
    const val IMPTABPROPS_RES = IMPTABPROPS_COLUMNS - 1
    const val IMPTABLEVELS_COLUMNS = _B + 2
    const val IMPTABLEVELS_RES = IMPTABLEVELS_COLUMNS - 1
    class Pair(table1: Array<ByteArray>, table2: Array<ByteArray>, act1: ShortArray, act2: ShortArray) {
        val imptab = arrayOf(table1, table2)
        val impact = arrayOf(act1, act2)
    }
    val groupProp = shortArrayOf(0, 1, 2, 7, 8, 3, 9, 6, 5, 4, 4, 10, 10, 12, 10, 10, 10, 11, 10, 4, 4, 4, 4, 13, 14)
    val impTabProps = arrayOf(
        shortArrayOf(1, 2, 4, 5, 7, 15, 17, 7, 9, 7, 0, 7, 3, 18, 21, 4),
        shortArrayOf(1, 34, 36, 37, 39, 47, 49, 39, 41, 39, 1, 1, 35, 50, 53, 0),
        shortArrayOf(33, 2, 36, 37, 39, 47, 49, 39, 41, 39, 2, 2, 35, 50, 53, 1),
        shortArrayOf(33, 34, 38, 38, 40, 48, 49, 40, 40, 40, 3, 3, 3, 50, 53, 1),
        shortArrayOf(33, 34, 4, 37, 39, 47, 49, 74, 11, 74, 4, 4, 35, 18, 21, 2),
        shortArrayOf(33, 34, 36, 5, 39, 47, 49, 39, 41, 76, 5, 5, 35, 50, 53, 3),
        shortArrayOf(33, 34, 6, 6, 40, 48, 49, 40, 40, 77, 6, 6, 35, 18, 21, 3),
        shortArrayOf(33, 34, 36, 37, 7, 47, 49, 7, 78, 7, 7, 7, 35, 50, 53, 4),
        shortArrayOf(33, 34, 38, 38, 8, 48, 49, 8, 8, 8, 8, 8, 35, 50, 53, 4),
        shortArrayOf(33, 34, 4, 37, 7, 47, 49, 7, 9, 7, 9, 9, 35, 18, 21, 4),
        shortArrayOf(97, 98, 4, 101, 135, 111, 113, 135, 142, 135, 10, 135, 99, 18, 21, 2),
        shortArrayOf(33, 34, 4, 37, 39, 47, 49, 39, 11, 39, 11, 11, 35, 18, 21, 2),
        shortArrayOf(97, 98, 100, 5, 135, 111, 113, 135, 142, 135, 12, 135, 99, 114, 117, 3),
        shortArrayOf(97, 98, 6, 6, 136, 112, 113, 136, 136, 136, 13, 136, 99, 18, 21, 3),
        shortArrayOf(33, 34, 132, 37, 7, 47, 49, 7, 14, 7, 14, 14, 35, 146, 149, 4),
        shortArrayOf(33, 34, 36, 37, 39, 15, 49, 39, 41, 39, 15, 39, 35, 50, 53, 5),
        shortArrayOf(33, 34, 38, 38, 40, 16, 49, 40, 40, 40, 16, 40, 35, 50, 53, 5),
        shortArrayOf(33, 34, 36, 37, 39, 47, 17, 39, 41, 39, 17, 39, 35, 50, 53, 6),
        shortArrayOf(33, 34, 18, 37, 39, 47, 49, 83, 20, 83, 18, 18, 35, 18, 21, 0),
        shortArrayOf(97, 98, 18, 101, 135, 111, 113, 135, 142, 135, 19, 135, 99, 18, 21, 0),
        shortArrayOf(33, 34, 18, 37, 39, 47, 49, 39, 20, 39, 20, 20, 35, 18, 21, 0),
        shortArrayOf(33, 34, 21, 37, 39, 47, 49, 86, 23, 86, 21, 21, 35, 18, 21, 3),
        shortArrayOf(97, 98, 21, 101, 135, 111, 113, 135, 142, 135, 22, 135, 99, 18, 21, 3),
        shortArrayOf(33, 34, 21, 37, 39, 47, 49, 39, 23, 39, 23, 23, 35, 18, 21, 3)
    )
    val impTabL_DEFAULT = arrayOf(
        byteArrayOf(0, 1, 0, 2, 0, 0, 0, 0),
        byteArrayOf(0, 1, 3, 3, 0x14, 0x14, 0, 1),
        byteArrayOf(0, 1, 0, 2, 0x15, 0x15, 0, 2),
        byteArrayOf(0, 1, 3, 3, 0x14, 0x14, 0, 2),
        byteArrayOf(0, 0x21, 0x33, 0x33, 4, 4, 0, 0),
        byteArrayOf(0, 0x21, 0, 0x32, 5, 5, 0, 0)
    )
    val impTabR_DEFAULT = arrayOf(
        byteArrayOf(1, 0, 2, 2, 0, 0, 0, 0),
        byteArrayOf(1, 0, 1, 3, 0x14, 0x14, 0, 1),
        byteArrayOf(1, 0, 2, 2, 0, 0, 0, 1),
        byteArrayOf(1, 0, 1, 3, 5, 5, 0, 1),
        byteArrayOf(0x21, 0, 0x21, 3, 4, 4, 0, 0),
        byteArrayOf(1, 0, 1, 3, 5, 5, 0, 0)
    )
    val impAct0 = shortArrayOf(0, 1, 2, 3, 4)
    val impTab_DEFAULT = Pair(impTabL_DEFAULT, impTabR_DEFAULT, impAct0, impAct0)
    val impTabL_NUMBERS_SPECIAL = arrayOf(
        byteArrayOf(0, 2, 0x11, 0x11, 0, 0, 0, 0),
        byteArrayOf(0, 0x42, 1, 1, 0, 0, 0, 0),
        byteArrayOf(0, 2, 4, 4, 0x13, 0x13, 0, 1),
        byteArrayOf(0, 0x22, 0x34, 0x34, 3, 3, 0, 0),
        byteArrayOf(0, 2, 4, 4, 0x13, 0x13, 0, 2)
    )
    val impTab_NUMBERS_SPECIAL = Pair(impTabL_NUMBERS_SPECIAL, impTabR_DEFAULT, impAct0, impAct0)
    val impTabL_GROUP_NUMBERS_WITH_R = arrayOf(
        byteArrayOf(0, 3, 0x11, 0x11, 0, 0, 0, 0),
        byteArrayOf(0x20, 3, 1, 1, 2, 0x20, 0x20, 2),
        byteArrayOf(0x20, 3, 1, 1, 2, 0x20, 0x20, 1),
        byteArrayOf(0, 3, 5, 5, 0x14, 0, 0, 1),
        byteArrayOf(0x20, 3, 5, 5, 4, 0x20, 0x20, 1),
        byteArrayOf(0, 3, 5, 5, 0x14, 0, 0, 2)
    )
    val impTabR_GROUP_NUMBERS_WITH_R = arrayOf(
        byteArrayOf(2, 0, 1, 1, 0, 0, 0, 0),
        byteArrayOf(2, 0, 1, 1, 0, 0, 0, 1),
        byteArrayOf(2, 0, 0x14, 0x14, 0x13, 0, 0, 1),
        byteArrayOf(0x22, 0, 4, 4, 3, 0, 0, 0),
        byteArrayOf(0x22, 0, 4, 4, 3, 0, 0, 1)
    )
    val impTab_GROUP_NUMBERS_WITH_R = Pair(impTabL_GROUP_NUMBERS_WITH_R, impTabR_GROUP_NUMBERS_WITH_R, impAct0, impAct0)
    val impTabL_INVERSE_NUMBERS_AS_L = arrayOf(
        byteArrayOf(0, 1, 0, 0, 0, 0, 0, 0),
        byteArrayOf(0, 1, 0, 0, 0x14, 0x14, 0, 1),
        byteArrayOf(0, 1, 0, 0, 0x15, 0x15, 0, 2),
        byteArrayOf(0, 1, 0, 0, 0x14, 0x14, 0, 2),
        byteArrayOf(0x20, 1, 0x20, 0x20, 4, 4, 0x20, 1),
        byteArrayOf(0x20, 1, 0x20, 0x20, 5, 5, 0x20, 1)
    )
    val impTabR_INVERSE_NUMBERS_AS_L = arrayOf(
        byteArrayOf(1, 0, 1, 1, 0, 0, 0, 0),
        byteArrayOf(1, 0, 1, 1, 0x14, 0x14, 0, 1),
        byteArrayOf(1, 0, 1, 1, 0, 0, 0, 1),
        byteArrayOf(1, 0, 1, 1, 5, 5, 0, 1),
        byteArrayOf(0x21, 0, 0x21, 0x21, 4, 4, 0, 0),
        byteArrayOf(1, 0, 1, 1, 5, 5, 0, 0)
    )
    val impTab_INVERSE_NUMBERS_AS_L = Pair(impTabL_INVERSE_NUMBERS_AS_L, impTabR_INVERSE_NUMBERS_AS_L, impAct0, impAct0)
    val impTabR_INVERSE_LIKE_DIRECT = arrayOf(
        byteArrayOf(1, 0, 2, 2, 0, 0, 0, 0),
        byteArrayOf(1, 0, 1, 2, 0x13, 0x13, 0, 1),
        byteArrayOf(1, 0, 2, 2, 0, 0, 0, 1),
        byteArrayOf(0x21, 0x30, 6, 4, 3, 3, 0x30, 0),
        byteArrayOf(0x21, 0x30, 6, 4, 5, 5, 0x30, 3),
        byteArrayOf(0x21, 0x30, 6, 4, 5, 5, 0x30, 2),
        byteArrayOf(0x21, 0x30, 6, 4, 3, 3, 0x30, 1)
    )
    val impAct1 = shortArrayOf(0, 1, 13, 14)
    val impTab_INVERSE_LIKE_DIRECT = Pair(impTabL_DEFAULT, impTabR_INVERSE_LIKE_DIRECT, impAct0, impAct1)
    val impTabL_INVERSE_LIKE_DIRECT_WITH_MARKS = arrayOf(
        byteArrayOf(0, 0x63, 0, 1, 0, 0, 0, 0),
        byteArrayOf(0, 0x63, 0, 1, 0x12, 0x30, 0, 4),
        byteArrayOf(0x20, 0x63, 0x20, 1, 2, 0x30, 0x20, 3),
        byteArrayOf(0, 0x63, 0x55, 0x56, 0x14, 0x30, 0, 3),
        byteArrayOf(0x30, 0x43, 0x55, 0x56, 4, 0x30, 0x30, 3),
        byteArrayOf(0x30, 0x43, 5, 0x56, 0x14, 0x30, 0x30, 4),
        byteArrayOf(0x30, 0x43, 0x55, 6, 0x14, 0x30, 0x30, 4)
    )
    val impTabR_INVERSE_LIKE_DIRECT_WITH_MARKS = arrayOf(
        byteArrayOf(0x13, 0, 1, 1, 0, 0, 0, 0),
        byteArrayOf(0x23, 0, 1, 1, 2, 0x40, 0, 1),
        byteArrayOf(0x23, 0, 1, 1, 2, 0x40, 0, 0),
        byteArrayOf(3, 0, 3, 0x36, 0x14, 0x40, 0, 1),
        byteArrayOf(0x53, 0x40, 5, 0x36, 4, 0x40, 0x40, 0),
        byteArrayOf(0x53, 0x40, 5, 0x36, 4, 0x40, 0x40, 1),
        byteArrayOf(0x53, 0x40, 6, 6, 4, 0x40, 0x40, 3)
    )
    val impAct2 = shortArrayOf(0, 1, 2, 5, 6, 7, 8)
    val impAct3 = shortArrayOf(0, 1, 9, 10, 11, 12)
    val impTab_INVERSE_LIKE_DIRECT_WITH_MARKS = Pair(impTabL_INVERSE_LIKE_DIRECT_WITH_MARKS, impTabR_INVERSE_LIKE_DIRECT_WITH_MARKS, impAct2, impAct3)
    val impTab_INVERSE_FOR_NUMBERS_SPECIAL = Pair(impTabL_NUMBERS_SPECIAL, impTabR_INVERSE_LIKE_DIRECT, impAct0, impAct1)
    val impTabL_INVERSE_FOR_NUMBERS_SPECIAL_WITH_MARKS = arrayOf(
        byteArrayOf(0, 0x62, 1, 1, 0, 0, 0, 0),
        byteArrayOf(0, 0x62, 1, 1, 0, 0x30, 0, 4),
        byteArrayOf(0, 0x62, 0x54, 0x54, 0x13, 0x30, 0, 3),
        byteArrayOf(0x30, 0x42, 0x54, 0x54, 3, 0x30, 0x30, 3),
        byteArrayOf(0x30, 0x42, 4, 4, 0x13, 0x30, 0x30, 4)
    )
    val impTab_INVERSE_FOR_NUMBERS_SPECIAL_WITH_MARKS = Pair(impTabL_INVERSE_FOR_NUMBERS_SPECIAL_WITH_MARKS, impTabR_INVERSE_LIKE_DIRECT_WITH_MARKS, impAct2, impAct3)
}
