// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.ICUConfig
import io.github.heapy.icu4k.impl.ResourceCacheLock
import io.github.heapy.icu4k.impl.createAvailabilityLock

/** TimeZone factory and default ownership policy, with explicit platform/owner boundaries. */
internal class TimeZoneRuntimeCore<Z : Any, H : Any>(
    private val owner: Owner<Z, H>,
    private val host: Host<H>,
    configuration: String? = ICUConfig.get("com.ibm.icu.util.TimeZone.DefaultTimeZoneType", "ICU"),
    private val lock: ResourceCacheLock = createAvailabilityLock(),
) {
    interface Owner<Z : Any, H : Any> {
        fun createJavaTimeZone(id: String?): Z?
        fun createDefaultJavaTimeZone(): Z
        fun getSystemTimeZone(id: String?): Z?
        fun getCustomTimeZone(id: String?): Z?
        fun unknownZone(): Z
        fun reportUnknownID(id: String?)
        fun isFrozen(zone: Z): Boolean
        fun clone(zone: Z): Z?
        fun freeze(zone: Z): Z?
        fun cloneAsThawed(zone: Z): Z?
        fun isJavaTimeZone(zone: Z): Boolean
        fun unwrapJavaTimeZone(zone: Z): H?
        fun isOlsonTimeZone(zone: Z): Boolean
        fun getID(zone: Z): String?
        fun getCanonicalID(id: String?): String?
        fun wrap(zone: Z): H
    }
    interface Host<H : Any> {
        /** ICU acquires the host default monitor before its own monitor on initialization. */
        fun <T> withDefaultLock(block: () -> T): T
        fun getDefault(): H
        fun getID(zone: H): String?
        fun getTimeZone(id: String?): H
        fun setDefault(zone: H)
    }

    @kotlin.concurrent.Volatile private var defaultZone: Z? = null
    private var implementation = if (configuration.equals("JDK", ignoreCase = true)) TIMEZONE_JDK else TIMEZONE_ICU

    fun getDefaultTimeZoneType(): Int = implementation

    fun setDefaultTimeZoneType(type: Int) = lock.withLock {
        require(type == TIMEZONE_ICU || type == TIMEZONE_JDK) { "Invalid timezone type" }
        implementation = type
    }

    fun getTimeZone(id: String?, type: Int = implementation): Z? = getTimeZone(id, type, false)
    fun getFrozenTimeZone(id: String?): Z? = getTimeZone(id, implementation, true)

    private fun getTimeZone(id: String?, type: Int, frozen: Boolean): Z? {
        var result: Z?
        if (type == TIMEZONE_JDK) {
            result = owner.createJavaTimeZone(id)
            if (result != null) return if (frozen) owner.freeze(result) else result
            result = getFrozenICUTimeZone(id, false)
        } else {
            result = getFrozenICUTimeZone(id, true)
        }
        if (result == null) {
            owner.reportUnknownID(id)
            result = owner.unknownZone()
        }
        return if (frozen) result else owner.cloneAsThawed(result)
    }

    fun getFrozenICUTimeZone(id: String?, trySystem: Boolean): Z? {
        val system = if (trySystem) owner.getSystemTimeZone(id) else null
        return system ?: owner.getCustomTimeZone(id)
    }

    fun getDefault(): Z? {
        var result = defaultZone
        if (result == null) {
            host.withDefaultLock {
                lock.withLock {
                    result = defaultZone
                    if (result == null) {
                        result = if (implementation == TIMEZONE_JDK) owner.createDefaultJavaTimeZone()
                        else getFrozenTimeZone(host.getID(host.getDefault()))
                        defaultZone = result
                    }
                }
            }
        }
        return owner.cloneAsThawed(result!!)
    }

    fun setICUDefault(zone: Z?) = lock.withLock {
        defaultZone = when {
            zone == null -> null
            owner.isFrozen(zone) -> zone
            else -> owner.freeze(owner.clone(zone)!!)
        }
    }

    fun setDefault(zone: Z?) = lock.withLock {
        setICUDefault(zone)
        if (zone != null) {
            var hostZone: H? = null
            if (owner.isJavaTimeZone(zone)) {
                hostZone = owner.unwrapJavaTimeZone(zone)
            } else if (owner.isOlsonTimeZone(zone)) {
                var id = owner.getID(zone)
                hostZone = host.getTimeZone(id)
                val hostID = host.getID(hostZone)
                if (id!! != hostID) {
                    id = owner.getCanonicalID(id)
                    hostZone = host.getTimeZone(id)
                    val canonicalHostID = host.getID(hostZone)
                    if (id!! != canonicalHostID) hostZone = null
                }
            }
            host.setDefault(hostZone ?: owner.wrap(zone))
        }
    }

    companion object {
        const val TIMEZONE_ICU = 0
        const val TIMEZONE_JDK = 1
    }
}
