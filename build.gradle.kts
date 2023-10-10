plugins {
    id("java-library")
    id("maven-publish")
    kotlin("jvm") version "1.9.10"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin.compilerOptions {
    jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.DavidArsene"
            artifactId = "arscblamer"

            afterEvaluate {
                from(components["java"])
            }
        }
    }
}
