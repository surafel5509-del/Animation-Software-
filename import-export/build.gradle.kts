plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    namespace = "com.animationstudio.importer"; compileSdk = libs.versions.compileSdk.get().toInt(); defaultConfig { minSdk = libs.versions.minSdk.get().toInt() } }
dependencies { api(project(":core")); implementation(project(":project-manager")); implementation(libs.androidx.core.ktx); implementation(libs.kotlinx.coroutines.android) }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
