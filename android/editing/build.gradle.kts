import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

// The editing rules shared by the Android and the iPhone app (PLAN-4 WP-3, approval A7): plain Kotlin
// on top of core, like core itself (ADR-0002); no platform APIs, enforced by core's ArchitectureTest.
plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(21)

    jvm {
        compilerOptions {
            // Android-compatible bytecode, like core.
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // The iPhone app links one framework with core and the editing rules (PLAN-4 section 3.1). It keeps
    // core's name, so Swift imports MandalaCore as before; core's own framework stays for SpikeBench.
    val xcf = XCFramework("MandalaCore")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "MandalaCore"
            isStatic = true
            export(project(":core"))
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

// Same entry point as core: the scripts call `:editing:test`.
tasks.register("test") {
    group = "verification"
    dependsOn("jvmTest")
}
