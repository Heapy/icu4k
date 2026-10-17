// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

internal actual fun linkTransliterationErrors(errors: List<RuntimeException>): RuntimeException = linkPortableTransliterationErrors(errors)
