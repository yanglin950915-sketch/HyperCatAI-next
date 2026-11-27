import java.io.File
import java.net.URL
import java.util.zip.ZipFile

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


val builtInAvatarAssets = layout.buildDirectory.dir("generated/builtinAvatarAssets")
val downloadBuiltInAvatar by tasks.registering {
    val sampleCommit = "b1de66b0b1f1cb881d95fb6158622aeb6a2827bd"
    inputs.property("live2dSampleCommit", sampleCommit)
    outputs.dir(builtInAvatarAssets)
    doLast {
        val outputRoot = builtInAvatarAssets.get().asFile
        val sampleCommit = "b1de66b0b1f1cb881d95fb6158622aeb6a2827bd"
        val models = listOf("Haru", "Hiyori", "Mao", "Natori", "Rice")
        val missing = models.filter { name ->
            val folder = outputRoot.resolve("avatar/${name.lowercase()}")
            !folder.resolve("$name.model3.json").isFile || !folder.resolve("$name.moc3").isFile
        }
        if (missing.isNotEmpty()) {
            val archive = temporaryDir.resolve("cubism-web-samples.zip")
            val archiveUrl = URL("https://codeload.github.com/Live2D/CubismWebSamples/zip/$sampleCommit")
            archiveUrl.openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 120_000
            }.getInputStream().use { input ->
                archive.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            ZipFile(archive).use { zip ->
                for (name in missing) {
                    val avatarDir = outputRoot.resolve("avatar/${name.lowercase()}")
                    val prefix = "CubismWebSamples-$sampleCommit/Samples/Resources/$name/"
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.name.startsWith(prefix)) continue
                        val relative = entry.name.removePrefix(prefix)
                        if (relative.isBlank()) continue
                        val target = avatarDir.resolve(relative)
                        require(target.canonicalPath.startsWith(avatarDir.canonicalPath + File.separator)) {
                            "Unsafe path in official Live2D sample archive: ${entry.name}"
                        }
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                target.outputStream().buffered().use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }
            }
        }
        for (name in models) {
            val avatarDir = outputRoot.resolve("avatar/${name.lowercase()}")
            check(avatarDir.resolve("$name.model3.json").isFile && avatarDir.resolve("$name.moc3").isFile) {
                "Official Live2D $name model download is incomplete"
            }
        }
    }
}

android {
    namespace = "com.hypercat.ai"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.hypercat.ai"
        minSdk = 26
        targetSdk = 35
        versionCode = 95
        versionName = "0.9.76"
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
    sourceSets.getByName("main").assets.srcDir(builtInAvatarAssets)
    buildFeatures { compose = true; aidl = true }
}
tasks.named("preBuild").configure { dependsOn(downloadBuiltInAvatar) }

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
