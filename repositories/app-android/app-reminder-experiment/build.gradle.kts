plugins {
    id("com.android.application")
}

android {
    namespace = "br.com.financasconscientes.reminderexperiment"
    compileSdk = 37

    defaultConfig {
        applicationId = "br.com.financasconscientes.reminderexperiment"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-experimental"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
