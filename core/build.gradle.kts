plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Android-compatible bytecode: the same module will be used by the Android app.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    exclude("**/MemoryBudgetTest*")
}

// Renders with the heap an Android app typically gets, so memory regressions fail the build.
val memoryTest by tasks.registering(Test::class) {
    description = "Renders under a 256 MB heap limit."
    group = "verification"
    useJUnitPlatform()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/MemoryBudgetTest*")
    maxHeapSize = "256m"
}

tasks.check {
    dependsOn(memoryTest)
}
