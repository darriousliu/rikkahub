import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.buildkonfig)
}

kotlin {
    android {
        namespace = "me.rerere.rikkahub.shared"
        compileSdk = 37
        minSdk = 26

        androidResources.enable = true

        withHostTest {}
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }

    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "RikkaHubShared"
            isStatic = true
        }
    }

    applyDefaultHierarchyTemplate()

    swiftPMDependencies {
        iosMinimumDeploymentTarget.set("15.0")
        swiftPackage(
            url = url("https://github.com/firebase/firebase-ios-sdk.git"),
            version = exact(libs.versions.firebaseApple.get()),
            products = listOf(
                product("FirebaseAnalytics"),
                product("FirebaseCrashlytics")
            )
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":ai"))
            implementation(project(":hugeicons"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            api(compose.components.resources)
            implementation(libs.androidx.room3.runtime)
            implementation(libs.androidx.room3.paging)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktorfit.lib.light)
            implementation(libs.coil.ktor3)
            implementation(libs.kscan)
            implementation(libs.dokar.quickjs)
            implementation(libs.ratex)
            implementation(libs.kermit)
            implementation(libs.jetbrains.markdown)
            implementation(libs.fleeksoft.ksoup)
            implementation(libs.ksoup.entities)
            implementation(libs.korlibs.template)
            implementation(libs.fast.kotlin.diff.core)
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs)
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.modelcontextprotocol.kotlin.sdk.client)
            implementation(libs.modelcontextprotocol.kotlin.sdk.core)
            implementation(libs.haze)
            implementation(libs.haze.blur)
            implementation(libs.haze.blur.materials)
            implementation(libs.image.viewer)
        }

        val mobileMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain {
            dependsOn(mobileMain)
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        iosMain {
            dependsOn(mobileMain)
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sentry)
            implementation(libs.jmdns)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.room3.testing)
        }
    }
}

buildkonfig {
    packageName = "me.rerere.rikkahub"

    defaultConfigs {
        buildConfigField(STRING, "VERSION_NAME", "2.4.5")
        buildConfigField(STRING, "VERSION_CODE", "172")
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "me.rerere.rikkahub.generated.resources"
}
