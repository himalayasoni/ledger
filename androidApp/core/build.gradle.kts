import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The core module is plain Kotlin: no Android, so its tests run in milliseconds
// on any computer, and the same rules could later be shared with an iPhone app.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.gson) // reads the shared contract file in tests
}
