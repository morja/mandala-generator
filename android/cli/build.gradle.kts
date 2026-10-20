plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.mathis.mandala.cli.MainKt")
    applicationName = "mandala"
    applicationDefaultJvmArgs = listOf("-Xmx3g")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "3g"
    // Parity tests render full images; run them only in the full check.
    if (!project.hasProperty("parity")) {
        exclude("**/ParityTest*")
    }
    systemProperty("mandala.root", rootProject.projectDir.absolutePath)
}
