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

            // floating
            // https://github.com/Petterpx/FloatingX
            api("io.github.petterpx:floatingx:2.3.7")
            api("io.github.petterpx:floatingx-compose:2.3.7")
        }
        commonMain.dependencies {
            // ktor
            api(libs.bundles.ktor)

            // kotlinx
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
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
}
