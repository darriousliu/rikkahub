import org.gradle.api.tasks.JavaExec
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

val ratexNativeTarget = run {
    val osName = System.getProperty("os.name").lowercase()
    val architecture = System.getProperty("os.arch").lowercase()
    val normalizedArchitecture = when (architecture) {
        "aarch64", "arm64" -> "aarch64"
        "x86_64", "amd64" -> "x86-64"
        else -> error("Unsupported RaTeX desktop architecture: $architecture")
    }
    when {
        "mac" in osName -> "darwin-$normalizedArchitecture"
        "linux" in osName -> "linux-$normalizedArchitecture"
        "windows" in osName && normalizedArchitecture == "x86-64" -> "windows-x86-64"
        else -> error("Unsupported RaTeX desktop host: $osName/$architecture")
    }
}

kotlin {
    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    jvmToolchain(21)

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":composeApp"))
            implementation(project(":speech"))
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.filekit.core)
            implementation(libs.ratex)
            runtimeOnly("io.github.darriousliu:ratex-native-$ratexNativeTarget:${libs.versions.ratex.get()}")
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
        jvmArgs("--enable-native-access=ALL-UNNAMED")

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

tasks.withType<JavaExec>().configureEach {
    if (name == "run" || name == "jvmRun") {
        jvmArgs("-Drikkahub.debug=true")
    }
}
