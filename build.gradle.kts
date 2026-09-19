plugins {
    java
    id("net.neoforged.moddev") version "2.0.107"
    kotlin("jvm") version "2.0.21"
}

group = property("mod_group_id") as String
version = property("mod_version") as String

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

neoForge {
    version = property("neo_version") as String

    runs {
        create("client") { client() }
        create("server") {
            server()
            programArgument("--nogui")
        }
    }

    mods {
        create("ziangts") {
            sourceSet(sourceSets.main.get())
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

dependencies {
    implementation("thedarkcolour:kotlinforforge-neoforge:5.7.0")
    // Cobblemon 1.8+ dependency is intentionally added after verifying its
    // published NeoForge Maven coordinates. Do not guess coordinates here.
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.jar {
    manifest {
        attributes["Implementation-Title"] = property("mod_name")
        attributes["Implementation-Version"] = project.version
    }
}
