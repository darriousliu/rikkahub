import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-multiplatform`
    com.android.kotlin.multiplatform.library
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "kotlinx.cinterop.ExperimentalForeignApi",
            "kotlinx.cinterop.BetaInteropApi",
            "kotlin.uuid.ExperimentalUuidApi",
            "androidx.compose.animation.ExperimentalSharedTransitionApi",
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.ui.ExperimentalComposeUiApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "com.mohamedrejeb.calf.permissions.ExperimentalPermissionsApi",
            "coil3.annotation.InternalCoilApi",
            "androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
        )
    }

    androidLibrary {
        minSdk = 26
        compileSdk {
            version = release(36)
        }

        compilerOptions {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

//    iosX64()
    iosArm64()
    iosSimulatorArm64()

    val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform(libs.findLibrary("koin-bom").get()))
            implementation(libs.findLibrary("koin-core").get())
        }
    }
}

