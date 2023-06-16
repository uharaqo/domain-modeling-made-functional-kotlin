plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jlleitschuh.gradle.ktlint-idea")
}

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    google()
    gradlePluginPortal()
}

group = "io.github.uharaqo"
version = "0.0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(19))
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    filter {
        isFailOnNoMatchingTests = false
    }
}

tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    verbose.set(true)
    android.set(false)
    outputToConsole.set(true)
    coloredOutput.set(true)
    outputColorName.set("RED")
    filter {
        exclude { it.file.path.contains("$buildDir/generated/") }
    }
}
