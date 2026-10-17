// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class BidiTransformTest {
    @Test fun sourceGoldensForAllSixteenSchemes() {
        // Captured independently from ICU4J 78.3 with mirroring, letter shaping and EN-to-AN digits.
        val expected = listOf(
"0061006200630020002805d005d105d200290020fee3feaefea3fe92fe8e0020066106620663",
"fee3feaefea3fe92fe8e00200661066206630020002905d005d105d200280020006100620063",
"0061006200630020002805d205d105d0002900200661066206630020fe8efe92fea3feaefee3",
"fee3feaefea3fe92fe8e00200663066206610020002905d005d105d200280020006300620061",
"066106620663002005d005d105d200280020fee3feaefea3fe92fe8e00290020006100620063",
"0061006200630020002905d005d105d200280020fee3feaefea3fe92fe8e0020066106620663",
"0661066206630020fe8efe92fea3feaefee30020002805d205d105d000290020006100620063",
"0063006200610020002905d005d105d200280020fee3feaefea3fe92fe8e0020066306620661",
"006100620063002000280661066206630020fe8dfe91fea4feaefee10020002805d205d105d0",
"0661066206630020fe8dfe91fea4feaefee10020002805d205d105d000290020006100620063",
"0061006200630020002805d005d105d200290020fee1feaefea4fe91fe8d0020066106620663",
"0663066206610020fe8dfe91fea4feaefee10020002905d205d105d000280020006300620061",
"066306620661002005d005d105d200280020fee3feaefea3fe92fe8e00280020006300620061",
"0063006200610020002905d005d105d200280020fee3feaefea3fe92fe8e0020066306620661",
"0663066206610020fe8efe92fea3feaefee30020002805d205d105d000280020006300620061",
"0061006200630020002805d005d105d200280020fee3feaefea3fe92fe8e0020066106620663",
        )
        val transform = BidiTransform()
        var i = 0
        for (inputOrder in BidiTransform.Order.entries) for (inputLevel in 0..1)
            for (outputOrder in BidiTransform.Order.entries) for (outputLevel in 0..1) {
                val actual = transform.transform("abc (אבג) مرحبا 123", inputLevel.toByte(), inputOrder,
                    outputLevel.toByte(), outputOrder, BidiTransform.Mirroring.ON, 8 or 32)
                val hex = actual.map { it.code.toString(16).padStart(4, '0') }.joinToString("")
                assertEquals(expected[i++], hex, "$inputOrder/$inputLevel -> $outputOrder/$outputLevel")
            }
    }
}
