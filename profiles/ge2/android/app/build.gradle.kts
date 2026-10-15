plugins {
    id("com.android.application")
}

android {
    namespace = "org.psprecomp.ge2"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "org.psprecomp.ge2"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-dev"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_PLATFORM=android-26",
                    "-DANDROID_ABI=arm64-v8a",
                    "-DPSPRECOMP_PROFILE=ge2",
                    "-DPSPRECOMP_BUILD_TESTS=OFF",
                    "-DPSPRECOMP_BUILD_PROFILE_TESTS=OFF",
                    "-DPSPRECOMP_GENERATED_OPT_LEVEL=2",
                    "-DCMAKE_BUILD_TYPE=Release"
                )
                targets += "ge2_native"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../../../../CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release { isMinifyEnabled = false }
    }

    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}
