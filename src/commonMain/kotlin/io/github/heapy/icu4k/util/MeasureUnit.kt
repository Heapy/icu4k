// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.CollectionSet
import io.github.heapy.icu4k.impl.MeasureUnitResourceCatalog
import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.impl.units.MeasureUnitImpl
import io.github.heapy.icu4k.impl.units.SingleUnitImpl
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** An immutable unit, identified by its canonical CLDR unit identifier. */
open class MeasureUnit : ICUSerializable {
    @JvmField protected val type: String?
    @JvmField protected val subType: String?
    private val measureUnitImpl: MeasureUnitImpl?
    protected constructor(type: String?, subType: String?) { this.type = type; this.subType = subType; measureUnitImpl = null }
    private constructor(impl: MeasureUnitImpl) { type = null; subType = null; measureUnitImpl = impl.copy() }
    enum class Complexity { SINGLE, COMPOUND, MIXED }
    enum class MeasurePrefix(private val power: Int, private val identifier: String, private val base: Int) {
        QUETTA(30, "quetta", 10),
        RONNA(27, "ronna", 10),
        YOTTA(24, "yotta", 10),
        ZETTA(21, "zetta", 10),
        EXA(18, "exa", 10),
        PETA(15, "peta", 10),
        TERA(12, "tera", 10),
        GIGA(9, "giga", 10),
        MEGA(6, "mega", 10),
        KILO(3, "kilo", 10),
        HECTO(2, "hecto", 10),
        DEKA(1, "deka", 10),
        ONE(0, "", 10),
        DECI(-1, "deci", 10),
        CENTI(-2, "centi", 10),
        MILLI(-3, "milli", 10),
        MICRO(-6, "micro", 10),
        NANO(-9, "nano", 10),
        PICO(-12, "pico", 10),
        FEMTO(-15, "femto", 10),
        ATTO(-18, "atto", 10),
        ZEPTO(-21, "zepto", 10),
        YOCTO(-24, "yocto", 10),
        RONTO(-27, "ronto", 10),
        QUECTO(-30, "quecto", 10),
        KIBI(1, "kibi", 1024),
        MEBI(2, "mebi", 1024),
        GIBI(3, "gibi", 1024),
        TEBI(4, "tebi", 1024),
        PEBI(5, "pebi", 1024),
        EXBI(6, "exbi", 1024),
        ZEBI(7, "zebi", 1024),
        YOBI(8, "yobi", 1024);
        @Deprecated("This API is ICU internal only.") fun getIdentifier(): String = identifier
        fun getBase(): Int = base
        fun getPower(): Int = power
    }
    open fun getType(): String? = type
    open fun getSubtype(): String? = subType
    open fun getIdentifier(): String = (if (measureUnitImpl == null) getSubtype() else measureUnitImpl.getIdentifier()) ?: ""
    open fun getComplexity(): Complexity? = maybeReference().getComplexity()
    private fun single(): SingleUnitImpl = maybeReference().getSingleUnitImpl()
    open fun withPrefix(prefix: MeasurePrefix?): MeasureUnit = single().also { it.setPrefix(prefix) }.build()
    open fun getPrefix(): MeasurePrefix? = single().getPrefix()
    open fun withConstantDenominator(denominator: Long): MeasureUnit {
        require(denominator >= 0) { "Denominator cannot be negative" }
        if (getComplexity() != Complexity.COMPOUND && getComplexity() != Complexity.SINGLE)
            throw UnsupportedOperationException("Constant denominator can only be applied to COMPOUND & SINGLE units")
        val impl = getCopyOfMeasureUnitImpl()
        impl.setConstantDenominator(denominator)
        impl.setComplexity(if (denominator == 0L && impl.getSingleUnits().size < 2) Complexity.SINGLE else Complexity.COMPOUND)
        return impl.build()
    }
    open fun getConstantDenominator(): Long {
        val impl = getCopyOfMeasureUnitImpl()
        if (impl.getComplexity() != Complexity.COMPOUND && impl.getComplexity() != Complexity.SINGLE)
            throw UnsupportedOperationException("Constant denominator is only supported for COMPOUND & SINGLE units")
        return impl.getConstantDenominator()
    }
    open fun getDimensionality(): Int = single().getDimensionality()
    open fun withDimensionality(dimensionality: Int): MeasureUnit = single().also { it.setDimensionality(dimensionality) }.build()
    open fun reciprocal(): MeasureUnit {
        if (getComplexity() == Complexity.COMPOUND && getConstantDenominator() != 0L)
            throw UnsupportedOperationException("Cannot take reciprocal of a unit with a constant denominator")
        return getCopyOfMeasureUnitImpl().also { it.takeReciprocal() }.build()
    }
    open fun product(other: MeasureUnit?): MeasureUnit {
        val impl = getCopyOfMeasureUnitImpl()
        if (other == null) return impl.build()
        val otherImpl = other.maybeReference()
        if (impl.getComplexity() == Complexity.MIXED || otherImpl.getComplexity() == Complexity.MIXED) throw UnsupportedOperationException()
        for (unit in otherImpl.getSingleUnits()) impl.appendSingleUnit(unit)
        val first = impl.getConstantDenominator(); val second = otherImpl.getConstantDenominator()
        if (first != 0L && second != 0L) throw UnsupportedOperationException("Cannot multiply units that both of them have a constant denominator")
        impl.setConstantDenominator(maxOf(first, second))
        return impl.build()
    }
    open fun splitToSingleUnits(): List<MeasureUnit> = maybeReference().getSingleUnits().mapTo(ArrayList()) { it.build() }
    override fun hashCode(): Int = 31 * type!!.hashCode() + subType!!.hashCode()
    override fun equals(other: Any?): Boolean = this === other || other is MeasureUnit && getIdentifier() == other.getIdentifier()
    override fun toString(): String = (if (measureUnitImpl == null) "$type-$subType" else measureUnitImpl.getIdentifier()) ?: ""
    open fun getCopyOfMeasureUnitImpl(): MeasureUnitImpl = measureUnitImpl?.copy() ?: MeasureUnitImpl.forIdentifier(getIdentifier())!!
    private fun maybeReference(): MeasureUnitImpl = measureUnitImpl ?: MeasureUnitImpl.forIdentifier(getIdentifier())!!
    private fun writeReplace(): Any = measureUnitSerialProxy(type, subType)
    /** Source-named serialization proxy; JVM uses the Externalizable wire protocol. */
    internal class MeasureUnitProxy() : MeasureUnitExternalizable {
        private var type: String? = null
        private var subType: String? = null
        constructor(type: String?, subType: String?) : this() { this.type = type; this.subType = subType }
        override fun writeExternal(out: MeasureUnitObjectOutput) {
            out.writeByte(0)
            out.writeUTF(type)
            out.writeUTF(subType)
            out.writeShort(0)
        }
        override fun readExternal(input: MeasureUnitObjectInput) {
            input.readByte()
            type = input.readUTF()
            subType = input.readUTF()
            val extra = input.readShort().toInt()
            if (extra > 0) input.read(ByteArray(extra), 0, extra)
        }
        private fun readResolve(): Any = internalGetInstance(type, subType)
        companion object { private const val serialVersionUID = -3910681415330989598L }
    }
    protected fun interface Factory { fun create(type: String, subType: String): MeasureUnit }
    companion object {
        private val cache = HashMap<String, MutableMap<String, MeasureUnit>>()
        // ICU4J uses a reentrant class monitor, including calls from custom factories.
        private val lock = createAvailabilityLock()
        private var cacheIsPopulated = false
        private fun populateCache() = lock.withLock { populateCacheUnlocked() }
        private fun populateCacheUnlocked() {
            if (!cacheIsPopulated) {
                cacheIsPopulated = true
                MeasureUnitResourceCatalog.populate { type, name -> instanceUnlocked(type, name) }
            }
        }
        @JvmStatic fun forIdentifier(identifier: String?): MeasureUnit? = if (identifier.isNullOrEmpty()) NoUnit.BASE else MeasureUnitImpl.forIdentifier(identifier)!!.build()
        @JvmStatic fun fromMeasureUnitImpl(impl: MeasureUnitImpl?): MeasureUnit {
            impl!!.serialize()
            return findBySubType(impl.getIdentifier()) ?: MeasureUnit(impl)
        }
        @JvmStatic fun getAvailableTypes(): Set<String> { populateCache(); return unitReadOnlySet(cache.keys) }
        @JvmStatic fun getAvailable(type: String?): Set<MeasureUnit> {
            populateCache()
            return cache[type]?.let { unitReadOnlySet(CollectionSet(it.values)) } ?: EmptyUnitSet
        }
        @JvmStatic fun getAvailable(): Set<MeasureUnit> = lock.withLock {
            val result = HashSet<MeasureUnit>()
            populateCacheUnlocked()
            for (units in cache.values) result.addAll(units.values)
            unitReadOnlySet(result)
        }
        @JvmStatic fun internalGetInstance(type: String?, subType: String?): MeasureUnit = lock.withLock { instanceUnlocked(type, subType) }
        private fun instanceUnlocked(type: String?, subType: String?): MeasureUnit {
            if (type == null || subType == null) throw NullPointerException("Type and subType must be non-null")
            if (type != "currency" && (type.any { it !in 'a'..'z' } || subType.any { it !in 'a'..'z' && it !in '0'..'9' && it != '-' }))
                throw IllegalArgumentException("The type or subType are invalid.")
            val factory = when (type) {
                "currency" -> Factory { _, name -> Currency.createForUnit(name) }
                "duration" -> Factory { unitType, name -> TimeUnit(unitType, name) }
                else -> Factory { unitType, name -> MeasureUnit(unitType, name) }
            }
            return addUnitUnlocked(type, subType, factory)
        }
        @JvmStatic fun findBySubType(subType: String?): MeasureUnit? {
            populateCache()
            return cache.values.firstNotNullOfOrNull { it[subType] }
        }
        @Suppress("EXPOSED_PARAMETER_TYPE")
        @JvmStatic protected fun addUnit(type: String, unitName: String, factory: Factory): MeasureUnit = lock.withLock { addUnitUnlocked(type, unitName, factory) }
        private fun addUnitUnlocked(type: String, unitName: String, factory: Factory): MeasureUnit {
            val units = cache.getOrPut(type) { HashMap() }
            val internedType = units.values.firstOrNull()?.type ?: type
            return units.getOrPut(unitName) { factory.create(internedType, unitName) }
        }
        @JvmField val G_FORCE: MeasureUnit = internalGetInstance("acceleration", "g-force")
        @JvmField val METER_PER_SECOND_SQUARED: MeasureUnit = internalGetInstance("acceleration", "meter-per-square-second")
        @JvmField val ARC_MINUTE: MeasureUnit = internalGetInstance("angle", "arc-minute")
        @JvmField val ARC_SECOND: MeasureUnit = internalGetInstance("angle", "arc-second")
        @JvmField val DEGREE: MeasureUnit = internalGetInstance("angle", "degree")
        @JvmField val RADIAN: MeasureUnit = internalGetInstance("angle", "radian")
        @JvmField val REVOLUTION_ANGLE: MeasureUnit = internalGetInstance("angle", "revolution")
        @JvmField val STERADIAN: MeasureUnit = internalGetInstance("angle", "steradian")
        @JvmField val ACRE: MeasureUnit = internalGetInstance("area", "acre")
        @JvmField val BU_JP: MeasureUnit = internalGetInstance("area", "bu-jp")
        @JvmField val CHO: MeasureUnit = internalGetInstance("area", "cho")
        @JvmField val DUNAM: MeasureUnit = internalGetInstance("area", "dunam")
        @JvmField val HECTARE: MeasureUnit = internalGetInstance("area", "hectare")
        @JvmField val SE_JP: MeasureUnit = internalGetInstance("area", "se-jp")
        @JvmField val SQUARE_CENTIMETER: MeasureUnit = internalGetInstance("area", "square-centimeter")
        @JvmField val SQUARE_FOOT: MeasureUnit = internalGetInstance("area", "square-foot")
        @JvmField val SQUARE_INCH: MeasureUnit = internalGetInstance("area", "square-inch")
        @JvmField val SQUARE_KILOMETER: MeasureUnit = internalGetInstance("area", "square-kilometer")
        @JvmField val SQUARE_METER: MeasureUnit = internalGetInstance("area", "square-meter")
        @JvmField val SQUARE_MILE: MeasureUnit = internalGetInstance("area", "square-mile")
        @JvmField val SQUARE_YARD: MeasureUnit = internalGetInstance("area", "square-yard")
        @JvmField val ITEM: MeasureUnit = internalGetInstance("concentr", "item")
        @JvmField val KARAT: MeasureUnit = internalGetInstance("concentr", "karat")
        @JvmField val KATAL: MeasureUnit = internalGetInstance("concentr", "katal")
        @JvmField val MILLIGRAM_OFGLUCOSE_PER_DECILITER: MeasureUnit = internalGetInstance("concentr", "milligram-ofglucose-per-deciliter")
        @JvmField val MILLIGRAM_PER_DECILITER: MeasureUnit = internalGetInstance("concentr", "milligram-ofglucose-per-deciliter")
        @JvmField val MILLIMOLE_PER_LITER: MeasureUnit = internalGetInstance("concentr", "millimole-per-liter")
        @JvmField val MOLE: MeasureUnit = internalGetInstance("concentr", "mole")
        @JvmField val OFGLUCOSE: MeasureUnit = internalGetInstance("concentr", "ofglucose")
        @JvmField val PART: MeasureUnit = internalGetInstance("concentr", "part")
        @JvmField val PART_PER_1E6: MeasureUnit = internalGetInstance("concentr", "part-per-1e6")
        @JvmField val PART_PER_MILLION: MeasureUnit = internalGetInstance("concentr", "part-per-1e6")
        @JvmField val PART_PER_1E9: MeasureUnit = internalGetInstance("concentr", "part-per-1e9")
        @JvmField val PERCENT: MeasureUnit = internalGetInstance("concentr", "percent")
        @JvmField val PERMILLE: MeasureUnit = internalGetInstance("concentr", "permille")
        @JvmField val PERMYRIAD: MeasureUnit = internalGetInstance("concentr", "permyriad")
        @JvmField val LITER_PER_100KILOMETERS: MeasureUnit = internalGetInstance("consumption", "liter-per-100-kilometer")
        @JvmField val LITER_PER_KILOMETER: MeasureUnit = internalGetInstance("consumption", "liter-per-kilometer")
        @JvmField val MILE_PER_GALLON: MeasureUnit = internalGetInstance("consumption", "mile-per-gallon")
        @JvmField val MILE_PER_GALLON_IMPERIAL: MeasureUnit = internalGetInstance("consumption", "mile-per-gallon-imperial")
        @JvmField val BIT: MeasureUnit = internalGetInstance("digital", "bit")
        @JvmField val BYTE: MeasureUnit = internalGetInstance("digital", "byte")
        @JvmField val GIGABIT: MeasureUnit = internalGetInstance("digital", "gigabit")
        @JvmField val GIGABYTE: MeasureUnit = internalGetInstance("digital", "gigabyte")
        @JvmField val KILOBIT: MeasureUnit = internalGetInstance("digital", "kilobit")
        @JvmField val KILOBYTE: MeasureUnit = internalGetInstance("digital", "kilobyte")
        @JvmField val MEGABIT: MeasureUnit = internalGetInstance("digital", "megabit")
        @JvmField val MEGABYTE: MeasureUnit = internalGetInstance("digital", "megabyte")
        @JvmField val PETABYTE: MeasureUnit = internalGetInstance("digital", "petabyte")
        @JvmField val TERABIT: MeasureUnit = internalGetInstance("digital", "terabit")
        @JvmField val TERABYTE: MeasureUnit = internalGetInstance("digital", "terabyte")
        @JvmField val CENTURY: MeasureUnit = internalGetInstance("duration", "century")
        @JvmField val DAY: TimeUnit = internalGetInstance("duration", "day") as TimeUnit
        @JvmField val DAY_PERSON: MeasureUnit = internalGetInstance("duration", "day-person")
        @JvmField val DECADE: MeasureUnit = internalGetInstance("duration", "decade")
        @JvmField val FORTNIGHT: MeasureUnit = internalGetInstance("duration", "fortnight")
        @JvmField val HOUR: TimeUnit = internalGetInstance("duration", "hour") as TimeUnit
        @JvmField val MICROSECOND: MeasureUnit = internalGetInstance("duration", "microsecond")
        @JvmField val MILLISECOND: MeasureUnit = internalGetInstance("duration", "millisecond")
        @JvmField val MINUTE: TimeUnit = internalGetInstance("duration", "minute") as TimeUnit
        @JvmField val MONTH: TimeUnit = internalGetInstance("duration", "month") as TimeUnit
        @JvmField val MONTH_PERSON: MeasureUnit = internalGetInstance("duration", "month-person")
        @JvmField val NANOSECOND: MeasureUnit = internalGetInstance("duration", "nanosecond")
        @JvmField val NIGHT: MeasureUnit = internalGetInstance("duration", "night")
        @JvmField val QUARTER: MeasureUnit = internalGetInstance("duration", "quarter")
        @JvmField val SECOND: TimeUnit = internalGetInstance("duration", "second") as TimeUnit
        @JvmField val WEEK: TimeUnit = internalGetInstance("duration", "week") as TimeUnit
        @JvmField val WEEK_PERSON: MeasureUnit = internalGetInstance("duration", "week-person")
        @JvmField val YEAR: TimeUnit = internalGetInstance("duration", "year") as TimeUnit
        @JvmField val YEAR_PERSON: MeasureUnit = internalGetInstance("duration", "year-person")
        @JvmField val AMPERE: MeasureUnit = internalGetInstance("electric", "ampere")
        @JvmField val COULOMB: MeasureUnit = internalGetInstance("electric", "coulomb")
        @JvmField val FARAD: MeasureUnit = internalGetInstance("electric", "farad")
        @JvmField val HENRY: MeasureUnit = internalGetInstance("electric", "henry")
        @JvmField val MILLIAMPERE: MeasureUnit = internalGetInstance("electric", "milliampere")
        @JvmField val OHM: MeasureUnit = internalGetInstance("electric", "ohm")
        @JvmField val SIEMENS: MeasureUnit = internalGetInstance("electric", "siemens")
        @JvmField val VOLT: MeasureUnit = internalGetInstance("electric", "volt")
        @JvmField val BECQUEREL: MeasureUnit = internalGetInstance("energy", "becquerel")
        @JvmField val BRITISH_THERMAL_UNIT: MeasureUnit = internalGetInstance("energy", "british-thermal-unit")
        @JvmField val BRITISH_THERMAL_UNIT_IT: MeasureUnit = internalGetInstance("energy", "british-thermal-unit-it")
        @JvmField val CALORIE: MeasureUnit = internalGetInstance("energy", "calorie")
        @JvmField val CALORIE_IT: MeasureUnit = internalGetInstance("energy", "calorie-it")
        @JvmField val ELECTRONVOLT: MeasureUnit = internalGetInstance("energy", "electronvolt")
        @JvmField val FOODCALORIE: MeasureUnit = internalGetInstance("energy", "foodcalorie")
        @JvmField val GRAY: MeasureUnit = internalGetInstance("energy", "gray")
        @JvmField val JOULE: MeasureUnit = internalGetInstance("energy", "joule")
        @JvmField val KILOCALORIE: MeasureUnit = internalGetInstance("energy", "kilocalorie")
        @JvmField val KILOJOULE: MeasureUnit = internalGetInstance("energy", "kilojoule")
        @JvmField val KILOWATT_HOUR: MeasureUnit = internalGetInstance("energy", "kilowatt-hour")
        @JvmField val SIEVERT: MeasureUnit = internalGetInstance("energy", "sievert")
        @JvmField val THERM_US: MeasureUnit = internalGetInstance("energy", "therm-us")
        @JvmField val KILOGRAM_FORCE: MeasureUnit = internalGetInstance("force", "kilogram-force")
        @JvmField val KILOWATT_HOUR_PER_100_KILOMETER: MeasureUnit = internalGetInstance("force", "kilowatt-hour-per-100-kilometer")
        @JvmField val NEWTON: MeasureUnit = internalGetInstance("force", "newton")
        @JvmField val POUND_FORCE: MeasureUnit = internalGetInstance("force", "pound-force")
        @JvmField val GIGAHERTZ: MeasureUnit = internalGetInstance("frequency", "gigahertz")
        @JvmField val HERTZ: MeasureUnit = internalGetInstance("frequency", "hertz")
        @JvmField val KILOHERTZ: MeasureUnit = internalGetInstance("frequency", "kilohertz")
        @JvmField val MEGAHERTZ: MeasureUnit = internalGetInstance("frequency", "megahertz")
        @JvmField val DOT: MeasureUnit = internalGetInstance("graphics", "dot")
        @JvmField val DOT_PER_CENTIMETER: MeasureUnit = internalGetInstance("graphics", "dot-per-centimeter")
        @JvmField val DOT_PER_INCH: MeasureUnit = internalGetInstance("graphics", "dot-per-inch")
        @JvmField val EM: MeasureUnit = internalGetInstance("graphics", "em")
        @JvmField val MEGAPIXEL: MeasureUnit = internalGetInstance("graphics", "megapixel")
        @JvmField val PIXEL: MeasureUnit = internalGetInstance("graphics", "pixel")
        @JvmField val PIXEL_PER_CENTIMETER: MeasureUnit = internalGetInstance("graphics", "pixel-per-centimeter")
        @JvmField val PIXEL_PER_INCH: MeasureUnit = internalGetInstance("graphics", "pixel-per-inch")
        @JvmField val ASTRONOMICAL_UNIT: MeasureUnit = internalGetInstance("length", "astronomical-unit")
        @JvmField val CENTIMETER: MeasureUnit = internalGetInstance("length", "centimeter")
        @JvmField val CHAIN: MeasureUnit = internalGetInstance("length", "chain")
        @JvmField val DECIMETER: MeasureUnit = internalGetInstance("length", "decimeter")
        @JvmField val EARTH_RADIUS: MeasureUnit = internalGetInstance("length", "earth-radius")
        @JvmField val FATHOM: MeasureUnit = internalGetInstance("length", "fathom")
        @JvmField val FOOT: MeasureUnit = internalGetInstance("length", "foot")
        @JvmField val FURLONG: MeasureUnit = internalGetInstance("length", "furlong")
        @JvmField val INCH: MeasureUnit = internalGetInstance("length", "inch")
        @JvmField val JO_JP: MeasureUnit = internalGetInstance("length", "jo-jp")
        @JvmField val KEN: MeasureUnit = internalGetInstance("length", "ken")
        @JvmField val KILOMETER: MeasureUnit = internalGetInstance("length", "kilometer")
        @JvmField val LIGHT_YEAR: MeasureUnit = internalGetInstance("length", "light-year")
        @JvmField val METER: MeasureUnit = internalGetInstance("length", "meter")
        @JvmField val MICROMETER: MeasureUnit = internalGetInstance("length", "micrometer")
        @JvmField val MILE: MeasureUnit = internalGetInstance("length", "mile")
        @JvmField val MILE_SCANDINAVIAN: MeasureUnit = internalGetInstance("length", "mile-scandinavian")
        @JvmField val MILLIMETER: MeasureUnit = internalGetInstance("length", "millimeter")
        @JvmField val NANOMETER: MeasureUnit = internalGetInstance("length", "nanometer")
        @JvmField val NAUTICAL_MILE: MeasureUnit = internalGetInstance("length", "nautical-mile")
        @JvmField val PARSEC: MeasureUnit = internalGetInstance("length", "parsec")
        @JvmField val PICOMETER: MeasureUnit = internalGetInstance("length", "picometer")
        @JvmField val POINT: MeasureUnit = internalGetInstance("length", "point")
        @JvmField val RI_JP: MeasureUnit = internalGetInstance("length", "ri-jp")
        @JvmField val RIN: MeasureUnit = internalGetInstance("length", "rin")
        @JvmField val ROD: MeasureUnit = internalGetInstance("length", "rod")
        @JvmField val SHAKU_CLOTH: MeasureUnit = internalGetInstance("length", "shaku-cloth")
        @JvmField val SHAKU_LENGTH: MeasureUnit = internalGetInstance("length", "shaku-length")
        @JvmField val SOLAR_RADIUS: MeasureUnit = internalGetInstance("length", "solar-radius")
        @JvmField val SUN: MeasureUnit = internalGetInstance("length", "sun")
        @JvmField val YARD: MeasureUnit = internalGetInstance("length", "yard")
        @JvmField val CANDELA: MeasureUnit = internalGetInstance("light", "candela")
        @JvmField val LUMEN: MeasureUnit = internalGetInstance("light", "lumen")
        @JvmField val LUX: MeasureUnit = internalGetInstance("light", "lux")
        @JvmField val SOLAR_LUMINOSITY: MeasureUnit = internalGetInstance("light", "solar-luminosity")
        @JvmField val TESLA: MeasureUnit = internalGetInstance("magnetic", "tesla")
        @JvmField val WEBER: MeasureUnit = internalGetInstance("magnetic", "weber")
        @JvmField val CARAT: MeasureUnit = internalGetInstance("mass", "carat")
        @JvmField val DALTON: MeasureUnit = internalGetInstance("mass", "dalton")
        @JvmField val EARTH_MASS: MeasureUnit = internalGetInstance("mass", "earth-mass")
        @JvmField val FUN: MeasureUnit = internalGetInstance("mass", "fun")
        @JvmField val GRAIN: MeasureUnit = internalGetInstance("mass", "grain")
        @JvmField val GRAM: MeasureUnit = internalGetInstance("mass", "gram")
        @JvmField val KILOGRAM: MeasureUnit = internalGetInstance("mass", "kilogram")
        @JvmField val MICROGRAM: MeasureUnit = internalGetInstance("mass", "microgram")
        @JvmField val MILLIGRAM: MeasureUnit = internalGetInstance("mass", "milligram")
        @JvmField val OUNCE: MeasureUnit = internalGetInstance("mass", "ounce")
        @JvmField val OUNCE_TROY: MeasureUnit = internalGetInstance("mass", "ounce-troy")
        @JvmField val POUND: MeasureUnit = internalGetInstance("mass", "pound")
        @JvmField val SLUG: MeasureUnit = internalGetInstance("mass", "slug")
        @JvmField val SOLAR_MASS: MeasureUnit = internalGetInstance("mass", "solar-mass")
        @JvmField val STONE: MeasureUnit = internalGetInstance("mass", "stone")
        @JvmField val TON: MeasureUnit = internalGetInstance("mass", "ton")
        @JvmField val TONNE: MeasureUnit = internalGetInstance("mass", "tonne")
        @JvmField val METRIC_TON: MeasureUnit = internalGetInstance("mass", "tonne")
        @JvmField val GIGAWATT: MeasureUnit = internalGetInstance("power", "gigawatt")
        @JvmField val HORSEPOWER: MeasureUnit = internalGetInstance("power", "horsepower")
        @JvmField val KILOWATT: MeasureUnit = internalGetInstance("power", "kilowatt")
        @JvmField val MEGAWATT: MeasureUnit = internalGetInstance("power", "megawatt")
        @JvmField val MILLIWATT: MeasureUnit = internalGetInstance("power", "milliwatt")
        @JvmField val WATT: MeasureUnit = internalGetInstance("power", "watt")
        @JvmField val ATMOSPHERE: MeasureUnit = internalGetInstance("pressure", "atmosphere")
        @JvmField val BAR: MeasureUnit = internalGetInstance("pressure", "bar")
        @JvmField val GASOLINE_ENERGY_DENSITY: MeasureUnit = internalGetInstance("pressure", "gasoline-energy-density")
        @JvmField val HECTOPASCAL: MeasureUnit = internalGetInstance("pressure", "hectopascal")
        @JvmField val INCH_HG: MeasureUnit = internalGetInstance("pressure", "inch-ofhg")
        @JvmField val KILOPASCAL: MeasureUnit = internalGetInstance("pressure", "kilopascal")
        @JvmField val MEGAPASCAL: MeasureUnit = internalGetInstance("pressure", "megapascal")
        @JvmField val MILLIBAR: MeasureUnit = internalGetInstance("pressure", "millibar")
        @JvmField val MILLIMETER_OF_MERCURY: MeasureUnit = internalGetInstance("pressure", "millimeter-ofhg")
        @JvmField val OFHG: MeasureUnit = internalGetInstance("pressure", "ofhg")
        @JvmField val PASCAL: MeasureUnit = internalGetInstance("pressure", "pascal")
        @JvmField val POUND_PER_SQUARE_INCH: MeasureUnit = internalGetInstance("pressure", "pound-force-per-square-inch")
        @JvmField val BEAUFORT: MeasureUnit = internalGetInstance("speed", "beaufort")
        @JvmField val KILOMETER_PER_HOUR: MeasureUnit = internalGetInstance("speed", "kilometer-per-hour")
        @JvmField val KNOT: MeasureUnit = internalGetInstance("speed", "knot")
        @JvmField val LIGHT_SPEED: MeasureUnit = internalGetInstance("speed", "light-speed")
        @JvmField val METER_PER_SECOND: MeasureUnit = internalGetInstance("speed", "meter-per-second")
        @JvmField val MILE_PER_HOUR: MeasureUnit = internalGetInstance("speed", "mile-per-hour")
        @JvmField val CELSIUS: MeasureUnit = internalGetInstance("temperature", "celsius")
        @JvmField val FAHRENHEIT: MeasureUnit = internalGetInstance("temperature", "fahrenheit")
        @JvmField val GENERIC_TEMPERATURE: MeasureUnit = internalGetInstance("temperature", "generic")
        @JvmField val KELVIN: MeasureUnit = internalGetInstance("temperature", "kelvin")
        @JvmField val RANKINE: MeasureUnit = internalGetInstance("temperature", "rankine")
        @JvmField val NEWTON_METER: MeasureUnit = internalGetInstance("torque", "newton-meter")
        @JvmField val POUND_FOOT: MeasureUnit = internalGetInstance("torque", "pound-force-foot")
        @JvmField val ACRE_FOOT: MeasureUnit = internalGetInstance("volume", "acre-foot")
        @JvmField val BARREL: MeasureUnit = internalGetInstance("volume", "barrel")
        @JvmField val BUSHEL: MeasureUnit = internalGetInstance("volume", "bushel")
        @JvmField val CENTILITER: MeasureUnit = internalGetInstance("volume", "centiliter")
        @JvmField val CUBIC_CENTIMETER: MeasureUnit = internalGetInstance("volume", "cubic-centimeter")
        @JvmField val CUBIC_FOOT: MeasureUnit = internalGetInstance("volume", "cubic-foot")
        @JvmField val CUBIC_INCH: MeasureUnit = internalGetInstance("volume", "cubic-inch")
        @JvmField val CUBIC_KILOMETER: MeasureUnit = internalGetInstance("volume", "cubic-kilometer")
        @JvmField val CUBIC_METER: MeasureUnit = internalGetInstance("volume", "cubic-meter")
        @JvmField val CUBIC_MILE: MeasureUnit = internalGetInstance("volume", "cubic-mile")
        @JvmField val CUBIC_YARD: MeasureUnit = internalGetInstance("volume", "cubic-yard")
        @JvmField val CUP: MeasureUnit = internalGetInstance("volume", "cup")
        @JvmField val CUP_IMPERIAL: MeasureUnit = internalGetInstance("volume", "cup-imperial")
        @JvmField val CUP_JP: MeasureUnit = internalGetInstance("volume", "cup-jp")
        @JvmField val CUP_METRIC: MeasureUnit = internalGetInstance("volume", "cup-metric")
        @JvmField val DECILITER: MeasureUnit = internalGetInstance("volume", "deciliter")
        @JvmField val DESSERT_SPOON: MeasureUnit = internalGetInstance("volume", "dessert-spoon")
        @JvmField val DESSERT_SPOON_IMPERIAL: MeasureUnit = internalGetInstance("volume", "dessert-spoon-imperial")
        @JvmField val DRAM: MeasureUnit = internalGetInstance("volume", "dram")
        @JvmField val DROP: MeasureUnit = internalGetInstance("volume", "drop")
        @JvmField val FLUID_OUNCE: MeasureUnit = internalGetInstance("volume", "fluid-ounce")
        @JvmField val FLUID_OUNCE_IMPERIAL: MeasureUnit = internalGetInstance("volume", "fluid-ounce-imperial")
        @JvmField val FLUID_OUNCE_METRIC: MeasureUnit = internalGetInstance("volume", "fluid-ounce-metric")
        @JvmField val GALLON: MeasureUnit = internalGetInstance("volume", "gallon")
        @JvmField val GALLON_IMPERIAL: MeasureUnit = internalGetInstance("volume", "gallon-imperial")
        @JvmField val HECTOLITER: MeasureUnit = internalGetInstance("volume", "hectoliter")
        @JvmField val JIGGER: MeasureUnit = internalGetInstance("volume", "jigger")
        @JvmField val KOKU: MeasureUnit = internalGetInstance("volume", "koku")
        @JvmField val KOSAJI: MeasureUnit = internalGetInstance("volume", "kosaji")
        @JvmField val LITER: MeasureUnit = internalGetInstance("volume", "liter")
        @JvmField val MEGALITER: MeasureUnit = internalGetInstance("volume", "megaliter")
        @JvmField val MILLILITER: MeasureUnit = internalGetInstance("volume", "milliliter")
        @JvmField val OSAJI: MeasureUnit = internalGetInstance("volume", "osaji")
        @JvmField val PINCH: MeasureUnit = internalGetInstance("volume", "pinch")
        @JvmField val PINT: MeasureUnit = internalGetInstance("volume", "pint")
        @JvmField val PINT_IMPERIAL: MeasureUnit = internalGetInstance("volume", "pint-imperial")
        @JvmField val PINT_METRIC: MeasureUnit = internalGetInstance("volume", "pint-metric")
        @JvmField val QUART: MeasureUnit = internalGetInstance("volume", "quart")
        @JvmField val QUART_IMPERIAL: MeasureUnit = internalGetInstance("volume", "quart-imperial")
        @JvmField val SAI: MeasureUnit = internalGetInstance("volume", "sai")
        @JvmField val SHAKU: MeasureUnit = internalGetInstance("volume", "shaku")
        @JvmField val TABLESPOON: MeasureUnit = internalGetInstance("volume", "tablespoon")
        @JvmField val TEASPOON: MeasureUnit = internalGetInstance("volume", "teaspoon")
        @JvmField val TO_JP: MeasureUnit = internalGetInstance("volume", "to-jp")
    }
}

internal expect fun measureUnitSerialProxy(type: String?, subType: String?): Any

internal fun <E> unitReadOnlySet(values: Set<E>): Set<E> = object : MutableSet<E> {
    override val size get() = values.size
    override fun isEmpty() = values.isEmpty()
    override fun contains(element: E) = values.contains(element)
    override fun containsAll(elements: Collection<E>) = values.containsAll(elements)
    override fun iterator(): MutableIterator<E> {
        val iterator = values.iterator()
        return object : MutableIterator<E> {
            override fun hasNext() = iterator.hasNext()
            override fun next() = iterator.next()
            override fun remove(): Nothing = throw UnsupportedOperationException()
        }
    }
    override fun add(element: E): Nothing = throw UnsupportedOperationException()
    override fun addAll(elements: Collection<E>): Nothing = throw UnsupportedOperationException()
    override fun remove(element: E): Nothing = throw UnsupportedOperationException()
    override fun removeAll(elements: Collection<E>): Nothing = throw UnsupportedOperationException()
    override fun retainAll(elements: Collection<E>): Nothing = throw UnsupportedOperationException()
    override fun clear(): Nothing = throw UnsupportedOperationException()
    override fun equals(other: Any?): Boolean = this === other || values == other
    override fun hashCode(): Int = values.hashCode()
    override fun toString(): String = values.toString()
}

// Collections.emptySet() permits no-op removals/clear; known types use an unmodifiable view.
private object EmptyUnitSet : MutableSet<MeasureUnit> {
    override val size = 0
    override fun isEmpty() = true
    override fun contains(element: MeasureUnit) = false
    override fun containsAll(elements: Collection<MeasureUnit>) = elements.isEmpty()
    override fun iterator(): MutableIterator<MeasureUnit> = object : MutableIterator<MeasureUnit> {
        override fun hasNext() = false
        override fun next(): Nothing = throw NoSuchElementException()
        override fun remove(): Nothing = throw IllegalStateException()
    }
    override fun add(element: MeasureUnit): Nothing = throw UnsupportedOperationException()
    override fun addAll(elements: Collection<MeasureUnit>): Boolean = if (elements.isEmpty()) false else throw UnsupportedOperationException()
    override fun remove(element: MeasureUnit) = false
    override fun removeAll(elements: Collection<MeasureUnit>) = false
    override fun retainAll(elements: Collection<MeasureUnit>) = false
    override fun clear() {}
    override fun equals(other: Any?): Boolean = other is Set<*> && other.isEmpty()
    override fun hashCode() = 0
    override fun toString() = "[]"
}

internal expect interface MeasureUnitExternalizable : ICUSerializable {
    fun writeExternal(out: MeasureUnitObjectOutput)
    fun readExternal(input: MeasureUnitObjectInput)
}
internal expect interface MeasureUnitObjectOutput {
    fun writeByte(value: Int)
    fun writeUTF(value: String?)
    fun writeShort(value: Int)
}
internal expect interface MeasureUnitObjectInput {
    fun readByte(): Byte
    fun readUTF(): String
    fun readShort(): Short
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
}
