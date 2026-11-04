plugins {
    id("com.android.application")
}

android {
    namespace = "br.com.financasconscientes"
    compileSdk = 37

    defaultConfig {
        applicationId = "br.com.financasconscientes"
        minSdk = 26
        targetSdk = 37
        versionCode = 14
        versionName = "0.6.5-experimental"
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

dependencies {
    testImplementation("junit:junit:4.13.2")
}
