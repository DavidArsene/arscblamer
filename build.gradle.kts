plugins {
    id("java-library")
    id("maven-publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.guava:guava:+")

    compileOnly("com.google.auto.value:auto-value-annotations:+")
    annotationProcessor("com.google.auto.value:auto-value:+")

    compileOnly("org.checkerframework:checker-compat-qual:+")
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
