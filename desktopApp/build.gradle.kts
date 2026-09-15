import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import dev.nucleusframework.desktop.application.dsl.CompressionLevel
import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.nucleus)
    alias(libs.plugins.kotlin.compose)
}

val desktopRuntime = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
    // Tao does not need JBR/JCEF; use a plain JDK for the packaged runtime and ProGuard library classes.
    vendor.set(JvmVendorSpec.ADOPTIUM)
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
            implementation(libs.cmp.material3)
            implementation(libs.nucleus.application)
            implementation(libs.nucleus.window.tao)
            // Nucleus primes the Windows process AUMID before creating a window only when this module is present.
            runtimeOnly(libs.nucleus.launcher.windows)
            implementation(libs.filekit.core)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.compose)
            implementation(libs.ratex)
            runtimeOnly("io.github.darriousliu:ratex-native-$ratexNativeTarget:${libs.versions.ratex.get()}")
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// ProGuard does not infer reachability from dependency META-INF/services entries.
// Keep just the registered providers (Ktor, Coil, ImageIO, etc.) and their service type names.
val generateDesktopServiceRules = tasks.register("generateDesktopServiceRules") {
    val runtimeJars = objects.fileCollection().from(configurations.named("jvmRuntimeClasspath"))
    val rulesFile = layout.buildDirectory.file("generated/proguard/services.pro")
    inputs.files(runtimeJars).withPropertyName("runtimeJars").withNormalizer(ClasspathNormalizer::class.java)
    outputs.file(rulesFile)
    dependsOn(runtimeJars)
    doLast {
        val rules = sortedSetOf<String>()
        runtimeJars.filter { it.extension == "jar" }.forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith("META-INF/services/") }
                    .forEach { entry ->
                        val service = entry.name.removePrefix("META-INF/services/")
                        rules += "-keep class $service { *; }"
                        zip.getInputStream(entry).bufferedReader().useLines { lines ->
                            lines.map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.forEach {
                                rules += "-keep class $it { *; }"
                            }
                        }
                    }
            }
        }
        rulesFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(rules.joinToString("\n", postfix = "\n"))
        }
    }
}

nucleus {
    application {
        mainClass = "me.rerere.rikkahub.desktop.MainKt"
        javaHome = desktopRuntime.get().metadata.installationPath.asFile.absolutePath
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        if (System.getProperty("os.name").startsWith("Mac")) {
            jvmArgs("-XstartOnFirstThread")
        }

        buildTypes.release.proguard {
            isEnabled.set(true)
            obfuscate.set(true)
            optimize.set(true)
            // Keep JAR resource/service boundaries used by native libraries and ServiceLoader.
            joinOutputJars.set(false)
            configurationFiles.from(project.file("proguard-rules.pro"))
            configurationFiles.from(generateDesktopServiceRules)
        }

        nativeDistributions {
            // DataStore/JNA need Unsafe; PDFBox and the HTTP stacks use the other modules.
            modules("jdk.unsupported", "java.management", "java.naming", "java.sql")
            includeAllModules = false
            cleanupNativeLibs = true
            compressionLevel = CompressionLevel.Maximum
            targetFormats(TargetFormat.Dmg, TargetFormat.Nsis)
            packageName = "RikkaHub"
            packageVersion = "2.4.5"

            macOS {
                bundleID = "me.rerere.rikkahub.desktop"
                iconFile.set(project.file("icons/RikkaHub.icns"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSMicrophoneUsageDescription</key>
                        <string>RikkaHub uses the microphone to convert your speech into chat input with your selected speech recognition provider.</string>
                        <key>NSCalendarsFullAccessUsageDescription</key>
                        <string>Allow assistants to query your calendar and create events after you approve the tool call.</string>
                        <key>NSCalendarsUsageDescription</key>
                        <string>Allow assistants to query your calendar and create events after you approve the tool call.</string>
                    """.trimIndent()
                }
            }

            windows {
                iconFile.set(project.file("icons/RikkaHub.ico"))
                nsis {
                    oneClick = false
                    perMachine = false
                    allowToChangeInstallationDirectory = true
                    createDesktopShortcut = true
                    createStartMenuShortcut = true
                    shortcutName = "RikkaHub"
                    deleteAppDataOnUninstall = false
                    installerIcon.set(project.file("icons/RikkaHub.ico"))
                    uninstallerIcon.set(project.file("icons/RikkaHub.ico"))
                }
            }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    if (name == "run" || name == "jvmRun") {
        jvmArgs("-Drikkahub.debug=true")
    }
}

val prepareDesktopProguardReports = tasks.register("prepareDesktopProguardReports") {
    val reportsDirectory = layout.buildDirectory.dir("reports/proguard")
    outputs.dir(reportsDirectory)
    doLast {
        reportsDirectory.get().asFile.mkdirs()
    }
}

tasks.matching { it.name == "proguardReleaseJars" }.configureEach {
    dependsOn(prepareDesktopProguardReports)
}
