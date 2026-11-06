plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.serialization) }
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    namespace = "com.animationstudio.rendering"; compileSdk = libs.versions.compileSdk.get().toInt(); defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }; testOptions { unitTests.isIncludeAndroidResources = true } }
dependencies {
    api(project(":core")); implementation(project(":animation-engine")); implementation(project(":camera-engine")); implementation(project(":vector-engine")); implementation(project(":effects-engine")); implementation(project(":project-manager"))
    implementation(libs.androidx.core.ktx); implementation(libs.kotlinx.coroutines.android); implementation(libs.kotlinx.serialization.json); implementation(libs.work.runtime)
    testImplementation(libs.junit)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
