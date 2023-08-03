import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    id("org.jmailen.kotlinter") version "3.15.0"

    `java-library`
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(group = "org.jetbrains.kotlin", name = "kotlin-test-junit5")
    testImplementation(group = "org.jetbrains.kotlin", name = "kotlin-test")
    testImplementation(group = "ch.qos.logback", name = "logback-classic", version = "1.4.8")

    implementation(group = "org.jetbrains.kotlinx", name = "atomicfu", version = "0.21.0")

    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = "1.7.3")

    implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8")

    implementation(group = "org.slf4j", name = "slf4j-api", version = "2.0.7")
    implementation(group = "com.github.walkyst.JAADec-fork", name = "jaadec-ext-aac", version = "0.1.3")

    api(group = "com.squareup.okhttp3", name = "okhttp", version = "5.0.0-alpha.11")
    implementation(group = "com.squareup.okhttp3", name = "okhttp-brotli", version = "5.0.0-alpha.11")

    api(group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version = "1.5.1")

    implementation(group = "commons-io", name = "commons-io", version = "2.13.0")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
