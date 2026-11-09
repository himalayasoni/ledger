import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.paparazzi) // screenshot tests: renders screens to PNG on the JVM, no phone needed
}

val appPackage = "com.himalayasoni.ledger.beta"

/**
 * Firebase settings come from google-services.json (downloaded from the Firebase console).
 * We read the few values we need here instead of using the google-services plugin, so the
 * build still works without the file (sync is then simply switched off).
 */
@Suppress("UNCHECKED_CAST")
val firebase: Map<String, String> = run {
    val f = file("google-services.json")
    if (!f.exists()) return@run emptyMap()
    val json = JsonSlurper().parse(f) as Map<String, Any?>
    val project = json["project_info"] as Map<String, Any?>
    val clients = json["client"] as List<Map<String, Any?>>
    fun info(c: Map<String, Any?>) = c["client_info"] as Map<String, Any?>
    val client = clients.firstOrNull { (info(it)["android_client_info"] as Map<String, Any?>)["package_name"] == appPackage }
        ?: return@run emptyMap()
    val oauth = clients.flatMap { c ->
        (c["oauth_client"] as? List<Map<String, Any?>>).orEmpty() +
            ((((c["services"] as? Map<String, Any?>)?.get("appinvite_service") as? Map<String, Any?>)
                ?.get("other_platform_oauth_client")) as? List<Map<String, Any?>>).orEmpty()
    }
    mapOf(
        "projectId" to (project["project_id"] as String),
        "apiKey" to (((client["api_key"] as List<Map<String, Any?>>).first())["current_key"] as String),
        "appId" to (info(client)["mobilesdk_app_id"] as String),
        "webClientId" to ((oauth.firstOrNull { (it["client_type"] as Number).toInt() == 3 }?.get("client_id") as String?) ?: ""),
    )
}

android {
    namespace = "com.himalayasoni.ledger"
    compileSdk = 34

    defaultConfig {
        // ".beta" lets the new app install next to the current one until it can replace it
        applicationId = appPackage
        minSdk = 26
        targetSdk = 34
        versionCode = 11
        versionName = "0.7.0"
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebase["projectId"] ?: ""}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebase["apiKey"] ?: ""}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebase["appId"] ?: ""}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${firebase["webClientId"] ?: ""}\"")
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
    buildFeatures { compose = true; buildConfig = true }
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

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.datastore.preferences)
    implementation(libs.biometric) // fingerprint / phone lock to show private entries

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
