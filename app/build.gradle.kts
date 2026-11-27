plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingStoreFile = providers.environmentVariable("HYPERCAT_KEYSTORE_PATH").orNull
    ?: error("Missing HYPERCAT_KEYSTORE_PATH. Configure the stable HyperCat AI signing secrets.")
val signingStorePassword = providers.environmentVariable("HYPERCAT_KEYSTORE_PASSWORD").orNull
    ?: error("Missing HYPERCAT_KEYSTORE_PASSWORD.")
val signingKeyAlias = providers.environmentVariable("HYPERCAT_KEY_ALIAS").orNull
    ?: error("Missing HYPERCAT_KEY_ALIAS.")
val signingKeyPassword = providers.environmentVariable("HYPERCAT_KEY_PASSWORD").orNull
    ?: error("Missing HYPERCAT_KEY_PASSWORD.")

android {
    namespace = "com.hypercat.ai"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.hypercat.ai"
        minSdk = 26
        targetSdk = 35
        versionCode = 60
        versionName = "0.9.41"
    }
    signingConfigs {
        create("stable") {
            storeFile = file(signingStoreFile)
            storePassword = signingStorePassword
            keyAlias = signingKeyAlias
            keyPassword = signingKeyPassword
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stable")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("stable")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("main").assets.srcDir("../rearscreen")
    buildFeatures { compose = true; aidl = true }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
