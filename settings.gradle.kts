pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.minecraftforge.net")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.impactdev.net/repository/development/")
        maven("https://maven.impactdev.net/repository/releases/")
        maven("https://thedarkcolour.github.io/KotlinForForge/")
    }
}
rootProject.name = "Zian-GTS"
