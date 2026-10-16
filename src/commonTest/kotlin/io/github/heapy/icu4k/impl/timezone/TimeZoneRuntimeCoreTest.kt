package io.github.heapy.icu4k.impl.timezone

import kotlin.test.*

class TimeZoneRuntimeCoreTest {
    private class Zone(var id: String, var frozen: Boolean = false)
    private class Fixture : TimeZoneRuntimeCore.Owner<Zone, Zone>, TimeZoneRuntimeCore.Host<Zone> {
        var host = Zone("host")
        var cloneFailure = false
        var hostFailure = false
        var lastCloneReceiver: Zone? = null
        var wrapped: Zone? = null
        var thawHook: (() -> Unit)? = null
        val core = TimeZoneRuntimeCore(this, this, "ICU")
        override fun createJavaTimeZone(id: String?) = id?.let { Zone(it) }
        override fun createDefaultJavaTimeZone() = Zone(host.id)
        override fun getSystemTimeZone(id: String?) = id?.let { Zone(it, true) }
        override fun getCustomTimeZone(id: String?): Zone? = null
        override fun unknownZone() = Zone("Etc/Unknown", true)
        override fun reportUnknownID(id: String?) = Unit
        override fun isFrozen(zone: Zone) = zone.frozen
        override fun clone(zone: Zone): Zone {
            if (cloneFailure) error("clone failed")
            return Zone(zone.id, zone.frozen)
        }
        override fun freeze(zone: Zone): Zone { zone.frozen = true; return zone }
        override fun cloneAsThawed(zone: Zone): Zone {
            lastCloneReceiver = zone
            val hook = thawHook
            thawHook = null
            hook?.invoke()
            return Zone(zone.id)
        }
        override fun isJavaTimeZone(zone: Zone) = false
        override fun unwrapJavaTimeZone(zone: Zone): Zone? = null
        override fun isOlsonTimeZone(zone: Zone) = false
        override fun getID(zone: Zone) = zone.id
        override fun getCanonicalID(id: String?) = id
        override fun wrap(zone: Zone): Zone { wrapped = zone; return zone }
        override fun <T> withDefaultLock(block: () -> T): T = block()
        override fun getDefault() = host
        override fun getTimeZone(id: String?) = Zone(id!!)
        override fun setDefault(zone: Zone) {
            if (hostFailure) error("host failed")
            host = zone
        }
    }

    @Test fun frozenIdentityMutableCopiesAndFailedUpdates() {
        val f = Fixture()
        val frozen = Zone("frozen", true)
        f.core.setICUDefault(frozen)
        assertFalse(f.core.getDefault()!!.frozen)
        assertSame(frozen, f.lastCloneReceiver)
        f.cloneFailure = true
        assertFailsWith<IllegalStateException> { f.core.setICUDefault(Zone("bad")) }
        assertEquals("frozen", f.core.getDefault()!!.id)
        f.cloneFailure = false
        val mutable = Zone("mutable")
        f.core.setICUDefault(mutable)
        mutable.id = "mutated"
        assertEquals("mutable", f.core.getDefault()!!.id)
        assertNotSame(mutable, f.lastCloneReceiver)
        assertTrue(f.lastCloneReceiver!!.frozen)
        f.hostFailure = true
        val replacement = Zone("replacement")
        assertFailsWith<IllegalStateException> { f.core.setDefault(replacement) }
        assertSame(replacement, f.wrapped)
        assertEquals("replacement", f.core.getDefault()!!.id)
        assertEquals("host", f.host.id)
    }

    @Test fun cacheSurvivesTypeChangesAndReentrantCloneUsesSnapshot() {
        val f = Fixture()
        assertEquals("host", f.core.getDefault()!!.id)
        f.host = Zone("new-host")
        f.core.setDefaultTimeZoneType(1)
        assertEquals("host", f.core.getDefault()!!.id)
        f.thawHook = { f.core.setICUDefault(Zone("reentrant", true)) }
        assertEquals("host", f.core.getDefault()!!.id)
        assertEquals("reentrant", f.core.getDefault()!!.id)
        f.core.setDefault(null)
        assertEquals("new-host", f.host.id)
        assertEquals("new-host", f.core.getDefault()!!.id)
        assertFalse(f.lastCloneReceiver!!.frozen) // JDK default construction is cached mutable.
        f.core.setDefaultTimeZoneType(0)
        f.core.setICUDefault(null)
        assertEquals("new-host", f.core.getDefault()!!.id)
        assertTrue(f.lastCloneReceiver!!.frozen)
    }
}
