import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

val desktopRuntime = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}

kotlin {
    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    jvmToolchain(21)

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":composeApp"))
            implementation(compose.desktop.currentOs)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "me.rerere.rikkahub.desktop.MainKt"
        javaHome = desktopRuntime.get().metadata.installationPath.asFile.absolutePath

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "RikkaHub"
            packageVersion = "2.4.5"

            macOS {
                bundleID = "me.rerere.rikkahub.desktop"
            }
        }
    }
}
