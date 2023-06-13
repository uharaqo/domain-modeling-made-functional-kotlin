plugins {
    id("kotlin-conventions")
    id("com.google.devtools.ksp") version libs.versions.kotlin.ksp.get()
    kotlin("plugin.serialization") version libs.versions.kotlin.asProvider().get()
}

dependencies {
    // internal

    // arrow
    implementation(libs.arrow.optics)
    ksp(libs.arrow.optics.ksp.plugin)

    // serialization
    implementation(libs.kotlinx.serialization.json)

    // test
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.arrow)
    testImplementation(libs.kotest.extensions.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.logback.classic)
}

buildscript {
    repositories { mavenCentral() }
    dependencies {
        val kotlinVersion = libs.versions.kotlin.asProvider().get()
        classpath(kotlin("gradle-plugin", version = kotlinVersion))
        classpath(kotlin("serialization", version = kotlinVersion))
    }
}
