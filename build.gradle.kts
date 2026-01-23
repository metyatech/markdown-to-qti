plugins {
    kotlin("jvm") version "2.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    val junitVersion = "6.0.2"
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
