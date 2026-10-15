package io.github.heapy.icu4k.util

import java.io.Externalizable
import java.io.ObjectInput
import java.io.ObjectOutput

internal actual fun measureUnitSerialProxy(type: String?, subType: String?): Any = MeasureUnitSerialProxy(type, subType)

internal class MeasureUnitSerialProxy() : Externalizable {
    private var type: String? = null
    private var subType: String? = null
    constructor(type: String?, subType: String?) : this() { this.type = type; this.subType = subType }
    override fun writeExternal(out: ObjectOutput) {
        out.writeByte(0)
        out.writeUTF(type!!)
        out.writeUTF(subType!!)
        out.writeShort(0)
    }
    override fun readExternal(input: ObjectInput) {
        input.readByte()
        type = input.readUTF()
        subType = input.readUTF()
        val extra = input.readShort().toInt()
        if (extra > 0) input.read(ByteArray(extra), 0, extra)
    }
    private fun readResolve(): Any = MeasureUnit.internalGetInstance(type, subType)
    companion object { private const val serialVersionUID: Long = -3910681415330989598L }
}
