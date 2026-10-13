plugins {
    kotlin("multiplatform") version "2.4.21"
}

group = "io.github.heapy"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvm()
    js() { nodejs() }
    linuxX64()
    macosArm64()
    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmTest.dependencies { implementation("com.ibm.icu:icu4j:78.3") }
    }
}
