plugins {
    alias(libs.plugins.android.library)
    id("multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))

            implementation(libs.ksoup)
        }
    }
}

android {
    namespace = "me.rerere.rag"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

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

dependencies {
    implementation(libs.jsoup)
}
