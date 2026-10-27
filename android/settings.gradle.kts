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

// Shared tearoom.one Android code (one.tearoom:unlock, ADR-0022), included as a composite build. Path from
// the Gradle property tearoomKitPath (e.g. in ~/.gradle/gradle.properties), else ~/Work/Projects/apps/tearoom-android-kit.
val tearoomKit = providers.gradleProperty("tearoomKitPath").orNull
    ?: "${System.getProperty("user.home")}/Work/Projects/apps/tearoom-android-kit"
check(file(tearoomKit).resolve("settings.gradle.kts").isFile) {
    "tearoom-android-kit not found at $tearoomKit; clone it there or set tearoomKitPath in ~/.gradle/gradle.properties"
}
includeBuild(tearoomKit)

rootProject.name = "mandala-app"
include(":core", ":cli", ":app")
