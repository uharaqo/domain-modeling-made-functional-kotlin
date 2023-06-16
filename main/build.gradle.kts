plugins {
    id("kotlin-conventions")
    kotlin("plugin.serialization") version libs.versions.kotlin.asProvider().get()
}

dependencies {
    // internal

    // arrow
    implementation(libs.arrow.core)

    // serialization
    implementation(libs.kotlinx.serialization.json)
}

buildscript {
    repositories { mavenCentral() }
    dependencies {
        val kotlinVersion = libs.versions.kotlin.asProvider().get()
        classpath(kotlin("gradle-plugin", version = kotlinVersion))
        classpath(kotlin("serialization", version = kotlinVersion))
    }
}
