plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.spotless.plugin.gradle)
    implementation(libs.ktlint.plugin)
    implementation(libs.ktlint.idea.plugin)
}
