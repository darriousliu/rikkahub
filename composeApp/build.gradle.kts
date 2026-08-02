import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.buildkonfig)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
    alias(libs.plugins.ktorfit)
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
        optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }

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
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(project(":ai"))
            implementation(project(":common"))
            implementation(project(":highlight"))
            implementation(project(":hugeicons"))
            implementation(project(":material3"))
            implementation(project(":search"))
            implementation(project(":speech"))
            implementation(project(":web"))
            api(libs.androidx.datastore.preferences)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            api(compose.components.resources)
            api(libs.androidx.room3.runtime)
            api(libs.androidx.room3.paging)
            api(libs.androidx.sqlite.bundled)
            implementation(libs.androidx.sqlite.async)
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktorfit.lib.light)
            implementation(libs.coil.ktor3)
            implementation(libs.coil.compose)
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
            implementation(libs.cache4k)
            implementation(libs.reorderable)
            implementation(libs.sonner)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.modelcontextprotocol.kotlin.sdk.client)
            implementation(libs.modelcontextprotocol.kotlin.sdk.core)
            implementation(libs.haze)
            implementation(libs.haze.blur)
            implementation(libs.haze.blur.materials)
            implementation(libs.image.viewer)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }

        val mobileMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain {
            dependsOn(mobileMain)
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.lifecycle.process)
                implementation(libs.coil.compose)
                implementation(libs.coil.gif)
                implementation(libs.coil.svg)
                implementation(libs.floatingx)
                implementation(libs.jmdns)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.metadata.extractor)
                implementation(libs.ucrop)
                implementation(libs.zxing.core)
            }
        }
        iosMain {
            dependsOn(mobileMain)
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        jvmMain.dependencies {
            implementation(libs.composewebview.jvm)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.sentry)
            implementation(libs.jmdns)
            implementation(libs.metadata.extractor)
            implementation(libs.zxing.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room3.compiler)
    add("kspJvm", libs.androidx.room3.compiler)
    add("kspIosArm64", libs.androidx.room3.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room3.compiler)
}

room3 {
    schemaDirectory("${rootProject.projectDir}/app/schemas")
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
