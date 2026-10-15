package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.text.PluralObjectInputStream
import io.github.heapy.icu4k.text.PluralObjectOutputStream

internal actual fun writeDecimalProperties(stream: PluralObjectOutputStream, properties: DecimalFormatProperties) {
    stream.defaultWriteObject()
    stream.writeInt(0)
    val defaults = DecimalFormatProperties.defaultsForStream()
    val fields = DecimalFormatProperties::class.java.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
    val values = fields.mapNotNull { field ->
        field.isAccessible = true
        val value = field.get(properties) ?: return@mapNotNull null
        if (!value.equals(field.get(defaults))) field.name to value else null
    }
    stream.writeInt(values.size)
    for ((name, value) in values) { stream.writeObject(name); stream.writeObject(value) }
}
internal actual fun readDecimalProperties(stream: PluralObjectInputStream, properties: DecimalFormatProperties) {
    stream.defaultReadObject()
    properties.clear()
    stream.readInt()
    val count = stream.readInt()
    val names = properties.entries().mapTo(HashSet()) { it.first } + setOf("DEFAULT", "serialVersionUID")
    repeat(count.coerceAtLeast(0)) {
        val name = stream.readObject() as String?
        val value = stream.readObject()
        if (name != null && name !in names) return@repeat
        val field = try { DecimalFormatProperties::class.java.getDeclaredField(name) }
        catch (_: NoSuchFieldException) { return@repeat }
        catch (e: SecurityException) { throw AssertionError(e) }
        try { field.isAccessible = true; field.set(properties, value) }
        catch (e: IllegalArgumentException) { throw AssertionError(e) }
        catch (e: IllegalAccessException) { throw AssertionError(e) }
    }
}
