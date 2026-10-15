package io.github.heapy.icu4k.impl.number

import java.net.URLClassLoader
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class ModifierReleaseOracleTest {
    @Test fun assertionsDisabledPreservesSourceMutationAndNullBehavior() {
        val urls = arrayOf(com.ibm.icu.impl.number.Modifier::class.java.protectionDomain.codeSource.location,
            Modifier::class.java.protectionDomain.codeSource.location, kotlin.Unit::class.java.protectionDomain.codeSource.location)
        URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { loader ->
            loader.setDefaultAssertionStatus(false)
            fun run(prefix: String): List<Any?> {
                val result = mutableListOf<Any?>()
                val storeClass = loader.loadClass("$prefix.AdoptingModifierStore")
                val modifierClass = loader.loadClass("$prefix.Modifier")
                val affixClass = loader.loadClass("$prefix.ConstantAffixModifier")
                val signClass = loader.loadClass("$prefix.Modifier\$Signum")
                val pluralClass = loader.loadClass(prefix.substringBeforeLast(".number") + ".StandardPlural")
                val sign = signClass.enumConstants.first { it.toString() == "POS" }
                val plural = pluralClass.enumConstants.first { it.toString() == "ONE" }
                val store = storeClass.getConstructor().newInstance()
                val modifier = affixClass.getConstructor().newInstance()
                val get = storeClass.getMethod("getModifier", signClass, pluralClass)
                result.add(get.invoke(store, sign, plural))
                storeClass.getMethod("freeze").invoke(store)
                storeClass.getMethod("setModifier", signClass, pluralClass, modifierClass).invoke(store, sign, plural, modifier)
                result.add(get.invoke(store, sign, plural).toString())
                result.add(storeClass.getMethod("getModifierWithoutPlural", signClass).invoke(store, sign))
                result.add(affixClass.getMethod("containsField", java.text.Format.Field::class.java).invoke(modifier, null))
                val simpleClass = loader.loadClass("$prefix.SimpleModifier")
                val simple = simpleClass.getConstructor(String::class.java, java.text.Format.Field::class.java, Boolean::class.javaPrimitiveType).newInstance(null, null, false)
                try { simpleClass.getMethod("getPrefixLength").invoke(simple) } catch (e: InvocationTargetException) { result.add(e.targetException.javaClass.simpleName) }
                return result
            }
            assertEquals(run("com.ibm.icu.impl.number"), run("io.github.heapy.icu4k.impl.number"))
        }
    }
}
