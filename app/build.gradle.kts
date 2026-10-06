plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.asdk.tools.vibrationlab"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.asdk.tools.vibrationlab"
        minSdk = 23
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.licensesdialog)
}
