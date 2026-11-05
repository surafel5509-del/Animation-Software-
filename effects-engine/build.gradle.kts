plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    } namespace = "com.animationstudio.effects"; compileSdk = libs.versions.compileSdk.get().toInt(); defaultConfig { minSdk = libs.versions.minSdk.get().toInt() } }
dependencies { api(project(":core")); implementation(libs.androidx.core.ktx); testImplementation(libs.junit) }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
