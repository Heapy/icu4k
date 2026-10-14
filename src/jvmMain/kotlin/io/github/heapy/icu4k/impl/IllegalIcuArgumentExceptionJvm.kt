package io.github.heapy.icu4k.impl

actual open class IllegalIcuArgumentException : IllegalArgumentException {
    actual constructor(message: String?) : super(message)
    actual constructor(cause: Throwable?) : super(cause)
    actual constructor(message: String?, cause: Throwable?) : super(message, cause)
    @Synchronized actual override fun initCause(cause: Throwable?): IllegalIcuArgumentException = super.initCause(cause) as IllegalIcuArgumentException
    companion object { private const val serialVersionUID = 3789261542830211225L }
}
