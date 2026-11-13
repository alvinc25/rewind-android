import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: no Android dependency. All report logic lives here and is unit-tested on the JVM.
// Only java.time is used, which maps 1:1 to kotlinx-datetime if this becomes a KMP `commonMain`.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Use cases are plain `suspend` functions: no coroutines library needed in main code.
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.core) // runBlocking in tests
}
