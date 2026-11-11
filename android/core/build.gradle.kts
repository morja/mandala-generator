import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)

    jvm {
        compilerOptions {
            // Android-compatible bytecode: the same module is used by the Android app.
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // The iPhone app links core as an XCFramework (ADR-0015, PLAN-4 section 6).
    val xcf = XCFramework("MandalaCore")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "MandalaCore"
            isStatic = true
            // Render threads help the collector mark instead of waiting for it: 5-20 % faster renders (SPIKE-3).
            binaryOption("gcMutatorsCooperate", "true")
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            // Guardrail rules shared by the tearoom.one apps (tearoom-android-kit, module :testing).
            implementation("one.tearoom:testing:1.0.0")
        }
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    exclude("**/MemoryBudgetTest*")
}

// The scripts and the reference renderer call `:core:test`; with Kotlin Multiplatform the JVM tests are `jvmTest`.
tasks.register("test") {
    group = "verification"
    dependsOn("jvmTest")
}

// Renders with the heap an Android app typically gets, so memory regressions fail the build.
val memoryTest = tasks.register<Test>("memoryTest") {
    description = "Renders under a 256 MB heap limit."
    group = "verification"
    useJUnitPlatform()
    val jvmTestCompilation = kotlin.jvm().compilations["test"]
    testClassesDirs = jvmTestCompilation.output.classesDirs
    classpath = files(jvmTestCompilation.runtimeDependencyFiles, jvmTestCompilation.output.allOutputs)
    include("**/MemoryBudgetTest*")
    maxHeapSize = "256m"
}

tasks.check {
    dependsOn(memoryTest)
}
