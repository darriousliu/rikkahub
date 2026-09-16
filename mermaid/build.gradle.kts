@file:Suppress("UnstableApiUsage")

import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

// Only this module needs its C declarations shared by iOS device and simulator.
extra["kotlin.mpp.enableCInteropCommonization"] = "true"

val nativeRoot = layout.buildDirectory.dir("native")
val windowsHost = System.getProperty("os.name").startsWith("Windows")
val macHost = System.getProperty("os.name").startsWith("Mac")

fun registerNativeBuild(name: String, platform: String, targets: List<String> = emptyList()) =
    tasks.register<Exec>(name) {
        group = "build"
        description = "Builds the pinned merman native library for $platform."
        workingDir(projectDir)
        // Cargo handles incremental builds. Always invoke it so changed Rust sources cannot
        // leave a stale library in an otherwise up-to-date Kotlin artifact.
        commandLine(
            if (windowsHost) {
                listOf(
                    providers.environmentVariable("MERMAN_PYTHON").getOrElse("python"),
                    file("scripts/build_native.py").absolutePath,
                    platform,
                ) + targets
            } else {
                listOf("bash", file("prepare-$platform-rust.sh").absolutePath) + targets
            }
        )
    }

fun requestedTargets(platform: String) = providers.gradleProperty("mermaid.$platform.targets")
    .map { value -> value.split(',').map(String::trim).filter(String::isNotEmpty) }
    .getOrElse(emptyList())

val prepareAndroidRust = registerNativeBuild("prepareAndroidRust", "android", requestedTargets("android"))
val prepareJvmRust = registerNativeBuild("prepareJvmRust", "jvm", requestedTargets("jvm"))

kotlin {
    android {
        namespace = "me.rerere.mermaid"
        compileSdk = 37
        minSdk = 26
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
        optimization {
            consumerKeepRules.file(file("consumer-rules.pro"))
            consumerKeepRules.publish = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { target ->
        val targetName = target.konanTarget.name
        val nativeDirectory = nativeRoot.get().dir("ios/$targetName")
        // Keep cinterop declarations on every host so Kotlin disables unsupported iOS metadata
        // cross-compilation instead of compiling iosMain without its generated FFI bindings.
        val interop = target.compilations.getByName("main").cinterops.create("mermaid") {
            defFile(file("src/nativeInterop/cinterop/mermaid.def"))
            includeDirs(file("native/include"))
            extraOpts("-libraryPath", nativeDirectory.asFile.absolutePath)
        }
        if (macHost) {
            val prepare = registerNativeBuild(
                "prepare${target.name.replaceFirstChar(Char::uppercaseChar)}Rust", "ios", listOf(targetName),
            )
            tasks.named(interop.interopProcessingTaskName).configure {
                dependsOn(prepare)
                // staticLibraries are embedded in the klib; cinterop doesn't track their contents itself.
                inputs.file(nativeDirectory.file("librikkahub_mermaid.a"))
            }
        }
        target.binaries.framework {
            baseName = "Mermaid"
            isStatic = true
        }
    }

    applyDefaultHierarchyTemplate()
    sourceSets {
        val androidJvmMain = create("androidJvmMain") { dependsOn(commonMain.get()) }
        androidMain {
            dependsOn(androidJvmMain)
            dependencies {
                implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
            }
        }
        jvmMain {
            dependsOn(androidJvmMain)
            resources.srcDir(nativeRoot.map { it.dir("jvm") })
            dependencies { implementation(libs.jna) }
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        named("androidDeviceTest").dependencies {
            implementation(libs.androidx.junit)
            implementation(libs.androidx.espresso.core)
        }
    }
}

extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
    onVariants { variant ->
        variant.sources.jniLibs?.addStaticSourceDirectory(nativeRoot.get().dir("android").asFile.absolutePath)
    }
}

tasks.matching { it.name == "mergeAndroidMainJniLibFolders" }.configureEach {
    dependsOn(prepareAndroidRust)
}
tasks.named("jvmProcessResources").configure { dependsOn(prepareJvmRust) }
