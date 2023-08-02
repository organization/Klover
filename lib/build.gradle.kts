import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    id("org.jmailen.kotlinter") version "3.15.0"

    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(group = "org.jetbrains.kotlin", name = "kotlin-test-junit5")
    testImplementation(group = "org.jetbrains.kotlin", name = "kotlin-test")

    implementation(group = "com.sedmelluq", name = "lava-common", version = "1.1.2")

    implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8")

    implementation(group = "org.slf4j", name = "slf4j-api", version = "2.0.7")

    api(group = "com.squareup.okhttp3", name = "okhttp", version = "5.0.0-alpha.11")
    implementation(group = "com.squareup.okhttp3", name = "okhttp-brotli", version = "5.0.0-alpha.11")

    api(group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version = "1.5.1")
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
