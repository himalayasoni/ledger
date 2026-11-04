plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.himalayasoni.ledger"
    compileSdk = 34

    defaultConfig {
        // ".beta" lets the new app install next to the current one until it can replace it
        applicationId = "com.himalayasoni.ledger.beta"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            val pass = System.getenv("LEDGER_KEY_PASS")
            storeFile = file("ledger.p12")
            storeType = "pkcs12"
            storePassword = pass
            keyAlias = "ledger"
            keyPassword = pass
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true      // R8 shrinks and optimises the code
            isShrinkResources = true    // drops unused images and layouts
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // CI signs with the real key; on your Mac, release builds fall back to the debug key
            signingConfig = if (System.getenv("LEDGER_KEY_PASS") != null) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas") // keeps a history of the database shape
}

dependencies {
    implementation(project(":core"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
