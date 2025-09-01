import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    id("multiplatform")
}

kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.material)

            api(libs.ktor.client.okhttp)

            // pebble (template engine)
            api(libs.pebble)

            // floating
            // https://github.com/Petterpx/FloatingX
            api("io.github.petterpx:floatingx:2.3.7")
            api("io.github.petterpx:floatingx-compose:2.3.7")
        }
        commonMain.dependencies {
            // Coil
            api(libs.coil.compose)
            // ktor
            api(libs.bundles.ktor)

            // kotlinx
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)

            // KMP Utils
            api(libs.mp.stools)
            api(libs.filekit.core)
            api(libs.filekit.dialogs.compose)

            // atomics
            api(libs.atomicfu)

            // Firebase
            api(libs.gitlive.firebase.analytics)
            api(libs.gitlive.firebase.crashlytics)
            api(libs.gitlive.firebase.config)

            api(libs.kermit)
        }
        iosMain.dependencies {
            api(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "me.rerere.common"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
    }
}
