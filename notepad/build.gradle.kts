import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

group = "com.example"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)
}

apply(from = "wix.gradle.kts")
compose.desktop {
    application {
        mainClass = "MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")
        jvmArgs += listOf("-Dstdout.encoding=UTF-8")
        jvmArgs += listOf("-Dstderr.encoding=UTF-8")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "notepad"
            packageVersion = "1.0.0"
        }
    }
}


