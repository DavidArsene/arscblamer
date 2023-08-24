plugins {
    id("java-library")
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
