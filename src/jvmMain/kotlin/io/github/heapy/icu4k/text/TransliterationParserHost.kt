// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

internal actual fun linkTransliterationErrors(errors: List<RuntimeException>): RuntimeException {
    for(i in errors.lastIndex downTo 1) {
        var previous=errors[i-1]
        while(previous.cause!=null)previous=previous.cause as RuntimeException
        previous.initCause(errors[i])
    }
    return errors[0]
}
