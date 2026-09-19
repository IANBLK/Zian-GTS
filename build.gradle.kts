import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    id("net.neoforged.moddev") version "2.0.107"
    kotlin("jvm") version "2.0.21"
}

group = property("mod_group_id") as String
version = property("mod_version") as String

base {
    archivesName.set("zian-gts")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
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
    maven("https://maven.impactdev.net/repository/development/")
}

dependencies {
    implementation("thedarkcolour:kotlinforforge-neoforge:${property("kotlin_for_forge_version")}")
    implementation("com.cobblemon:neoforge:${property("cobblemon_version")}") {
        isTransitive = false
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

tasks.jar {
    manifest {
        attributes["Implementation-Title"] = property("mod_name")
        attributes["Implementation-Version"] = project.version
    }
}
