package io.github.heapy.icu4k.impl

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URLClassLoader
import kotlin.test.*

class StringPrepReaderDebugOracleTest {
    @Test fun isolatedReaderDebugOutputMatchesSource() {
        val bytes=ICUBinary.getRequiredData("rfc3491.spp").let{it.readBytes(it.remaining)}
        val original=System.getProperty("ICUDebug");val stdout=System.out
        fun capture(source: Boolean): String {
            val urls=if(source)arrayOf(com.ibm.icu.impl.StringPrepDataReader::class.java.protectionDomain.codeSource.location)
                else arrayOf(StringPrepDataReader::class.java.protectionDomain.codeSource.location,Unit::class.java.protectionDomain.codeSource.location)
            val output=ByteArrayOutputStream()
            URLClassLoader(urls,null).use {loader->
                PrintStream(output,true,"UTF-8").use {System.setOut(it)
                    if(source) {
                        loader.loadClass("com.ibm.icu.impl.StringPrepDataReader").getConstructor(java.nio.ByteBuffer::class.java).newInstance(java.nio.ByteBuffer.wrap(bytes))
                    }else {
                        val order=loader.loadClass("io.github.heapy.icu4k.util.BinaryBuffer\$ByteOrder")
                        val buffer=loader.loadClass("io.github.heapy.icu4k.util.BinaryBuffer")
                        val input=buffer.getConstructor(ByteArray::class.java,order,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Boolean::class.javaPrimitiveType).newInstance(bytes,order.enumConstants[0],0,bytes.size,false)
                        loader.loadClass("io.github.heapy.icu4k.impl.StringPrepDataReader").getConstructor(buffer).newInstance(input)
                    }
                }
            }
            System.setOut(stdout)
            return output.toString("UTF-8")
        }
        try {
            for(flag in listOf("NormalizerDataReader","help,NormalizerDataReader","other")) {
                System.setProperty("ICUDebug",flag)
                assertEquals(capture(true),capture(false),flag)
            }
        }finally {
            System.setOut(stdout)
            if(original==null)System.clearProperty("ICUDebug")else System.setProperty("ICUDebug",original)
        }
    }
}
