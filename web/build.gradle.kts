import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

val webUiDir = rootProject.layout.projectDirectory.dir("web-ui")
val webResourcesDir = layout.projectDirectory.dir("src/androidMain/resources")
val webStaticResourcesDir = webResourcesDir.dir("static")

val buildWebUi = tasks.register<Exec>("buildWebUi") {
    group = "build"
    description = "Build web-ui and copy its static output into the web module resources."

    workingDir = webUiDir.asFile
    when {
        Os.isFamily(Os.FAMILY_MAC) -> commandLine("zsh", "-ic", "pnpm run build")
        Os.isFamily(Os.FAMILY_WINDOWS) -> commandLine("cmd", "/c", "pnpm run build")
        else -> commandLine("pnpm", "run", "build")
    }

    inputs.files(
        webUiDir.file("package.json"),
        webUiDir.file("pnpm-lock.yaml"),
        webUiDir.file("components.json"),
        webUiDir.file("copy.ts"),
        webUiDir.file("react-router.config.ts"),
        webUiDir.file("tsconfig.json"),
        webUiDir.file("vite.config.ts"),
        webUiDir.file("vite-env.d.ts")
    )
    inputs.dir(webUiDir.dir("app"))
    inputs.dir(webUiDir.dir("public"))
    outputs.dir(webStaticResourcesDir)
}

kotlin {
    android {
        namespace = "me.rerere.rikkahub.web"
        compileSdk = 37
        minSdk = 24

        withHostTest {}
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }

    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }

        val androidJvmMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.ktor.server.default.headers)
                implementation(libs.ktor.server.conditional.headers)
                implementation(libs.ktor.server.compression)
                implementation(libs.ktor.server.cors)
                api(libs.ktor.server.auth)
                api(libs.ktor.server.auth.jwt)
                api(libs.ktor.server.core)
                implementation(libs.ktor.server.host.common)
                api(libs.ktor.server.content.negotiation)
                api(libs.ktor.server.status.pages)
                api(libs.ktor.server.sse)
                api(libs.ktor.server.cio)
            }
        }

        androidMain {
            dependsOn(androidJvmMain)
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.appcompat)
                implementation(libs.material)
            }
        }

        jvmMain {
            dependsOn(androidJvmMain)
            resources.srcDir(webResourcesDir)
        }

        val androidJvmTest by creating {
            dependsOn(commonTest.get())
            dependencies {
                implementation(libs.junit)
            }
        }

        named("androidHostTest") {
            dependsOn(androidJvmTest)
        }
        named("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.androidx.espresso.core)
                implementation(libs.junit)
            }
        }
        jvmTest {
            dependsOn(androidJvmTest)
        }
    }
}

tasks.matching {
        it.name == "androidPreBuild" ||
        it.name == "preBuild" ||
        it.name == "compileAndroidMain" ||
        it.name == "processAndroidMainJavaRes" ||
        it.name == "jvmProcessResources"
}.configureEach {
    dependsOn(buildWebUi)
}
