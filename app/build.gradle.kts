import java.util.Properties

plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose); alias(libs.plugins.kotlin.serialization) }

val signingProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val signingStorePath = System.getenv("ANDROID_KEYSTORE_FILE") ?: signingProperties.getProperty("storeFile")
val signingStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: signingProperties.getProperty("storePassword")
val signingKeyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: signingProperties.getProperty("keyAlias")
val signingKeyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: signingProperties.getProperty("keyPassword")
val hasReleaseSigning = !signingStorePath.isNullOrBlank() && !signingStorePassword.isNullOrBlank() &&
    !signingKeyAlias.isNullOrBlank() && !signingKeyPassword.isNullOrBlank()

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    namespace = "com.animationstudio.app"; compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.animationstudio.app"; minSdk = libs.versions.minSdk.get().toInt(); targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1; versionName = "0.1.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(signingStorePath)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    buildTypes { release {
        isMinifyEnabled = false
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
    } }
    buildFeatures { compose = true }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt") }
}
dependencies {
    implementation(project(":core")); implementation(project(":core-ui")); implementation(project(":graphics-engine")); implementation(project(":vector-engine")); implementation(project(":animation-engine")); implementation(project(":timeline-engine")); implementation(project(":rigging-engine")); implementation(project(":drawing-tools")); implementation(project(":layers-engine")); implementation(project(":audio-engine")); implementation(project(":camera-engine")); implementation(project(":effects-engine")); implementation(project(":rendering-engine")); implementation(project(":project-manager")); implementation(project(":import-export")); implementation(project(":file-format")); implementation(project(":settings"))
    implementation(libs.androidx.core.ktx); implementation(libs.androidx.activity.compose); implementation(libs.androidx.lifecycle.runtime.compose); implementation(libs.androidx.lifecycle.viewmodel.compose); implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(platform(libs.compose.bom)); implementation(libs.compose.ui); implementation(libs.compose.ui.graphics); implementation(libs.compose.material3); implementation(libs.compose.material.icons); implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android); implementation(libs.kotlinx.serialization.json); implementation(libs.work.runtime)
    debugImplementation(libs.compose.ui.tooling); debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit); testImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit); androidTestImplementation(libs.androidx.espresso); androidTestImplementation(platform(libs.compose.bom)); androidTestImplementation(libs.compose.ui.test.junit4)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
