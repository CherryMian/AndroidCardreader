plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // No runtime dependencies in original project
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.apache.commons:commons-lang3:3.12.0")
    testImplementation("commons-io:commons-io:2.11.0")
}

