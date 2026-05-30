plugins {
    kotlin("jvm") version "2.1.0"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("com.gradleup.shadow") version "8.3.10"
}

group = "project.kompass"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")

    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(21)
}

tasks {
    shadowJar {
        relocate("kotlin", "project.kompass.journeysEndFarlands.libs.kotlin")
    }

    assemble {
        dependsOn(reobfJar)
    }
}