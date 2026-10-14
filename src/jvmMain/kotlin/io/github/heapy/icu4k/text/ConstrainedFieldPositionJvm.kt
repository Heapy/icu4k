package io.github.heapy.icu4k.text

/** JVM Class constraints preserve Class.isAssignableFrom field matching. */
fun ConstrainedFieldPosition.constrainClass(classConstraint: Class<*>?) {
    if (classConstraint == null) throw IllegalArgumentException("Cannot constrain on null field class")
    constrainClass(classConstraint.kotlin)
}
