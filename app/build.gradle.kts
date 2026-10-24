import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// deploy.sh と共有する設定値
val lockrec = Properties().apply { rootProject.file("lockrec.properties").inputStream().use(::load) }

android {
    namespace = "dev.thada.lockrec"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = lockrec.getProperty("package")
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        for (key in listOf("model_file", "github_owner", "github_repo", "github_branch", "minute_dir")) {
            buildConfigField("String", key.uppercase(), "\"${lockrec.getProperty(key)}\"")
        }

        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
            }
        }
    }

    buildTypes {
        debug {
            // deploy.sh が run-as でモデルとトークンを書き込むため debuggable のまま使う。whisper は常に最適化ビルド
            externalNativeBuild { cmake { arguments += "-DCMAKE_BUILD_TYPE=Release" } }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.work:work-runtime-ktx:2.10.3")
    testImplementation("junit:junit:4.13.2")
}
