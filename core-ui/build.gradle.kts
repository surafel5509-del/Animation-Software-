plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    } namespace = "com.animationstudio.coreui"; compileSdk = libs.versions.compileSdk.get().toInt(); defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }; buildFeatures { compose = true } }
dependencies {
    implementation(platform(libs.compose.bom)); api(libs.compose.ui); api(libs.compose.ui.graphics); api(libs.compose.material3); api(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview); debugImplementation(libs.compose.ui.tooling); implementation(libs.kotlinx.coroutines.android)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
