pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// Shared tearoom.one Android code (one.tearoom:unlock, ADR-0022), included as a composite build from
// https://github.com/morja/tearoom-android-kit: the Gradle property tearoomKitPath, else a checkout next to
// this repository, else ~/Work/Projects/apps/tearoom-android-kit.
val tearoomKit = providers.gradleProperty("tearoomKitPath").orNull
    ?: listOf(
        rootDir.resolve("../tearoom-android-kit"),
        rootDir.resolve("../../tearoom-android-kit"),
        file("${System.getProperty("user.home")}/Work/Projects/apps/tearoom-android-kit"),
    ).firstOrNull { it.resolve("settings.gradle.kts").isFile }?.canonicalPath
    ?: error("tearoom-android-kit not found: clone https://github.com/morja/tearoom-android-kit next to this repository or set tearoomKitPath")
includeBuild(tearoomKit)

rootProject.name = "mandala-app"
include(":core", ":cli", ":app")
