plugins {
    id("com.android.application")
}

android {
    namespace = "br.com.financasconscientes.diagnostic"
    compileSdk = 37

    defaultConfig {
        applicationId = "br.com.financasconscientes.diagnostic"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}
