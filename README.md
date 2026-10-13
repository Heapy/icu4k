# ICU4K

Incremental port of Unicode ICU4J 78.3 to Common Kotlin.

**Work in progress: this is not yet a replacement for ICU4J.**
Algorithms belong in `commonMain`; ICU4J may be used only as a JVM test oracle.
Targets: JVM, JavaScript/Node, Linux x64 and macOS arm64.
Toolchain: Kotlin 2.4.21, Gradle 9.8.1; JDK 25 to run Gradle.

```sh
./gradlew jvmTest jsNodeTest
# On Apple Silicon:
./gradlew macosArm64Test
```

See [porting scope](docs/PORTING.md) for measured source size and stages.
Unicode's upstream license and third-party notices are retained in LICENSE.
