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
    // Headless: text is drawn with AWT, which otherwise opens a Dock app on macOS and takes the focus.
    applicationDefaultJvmArgs = listOf("-Xmx3g", "-Djava.awt.headless=true")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "3g"
    // Parity tests render full images; run them only in the full check.
    if (!project.hasProperty("parity")) {
        exclude("**/ParityTest*")
    }
    systemProperty("mandala.root", rootProject.projectDir.absolutePath)
    systemProperty("java.awt.headless", "true")
}
