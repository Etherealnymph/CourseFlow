plugins {
    id("com.android.application")
}

android {
    namespace = "com.etherealnymph.courseflow"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.etherealnymph.courseflow"
        minSdk = 29
        targetSdk = 34
        versionCode = 5
        versionName = "1.2.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
