plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.ksp) }
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    namespace = "com.animationstudio.projectmanager"; compileSdk = libs.versions.compileSdk.get().toInt(); defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }; buildFeatures { buildConfig = true } }
dependencies {
    api(project(":core")); api(project(":file-format")); implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android); implementation(libs.room.runtime); implementation(libs.room.ktx); ksp(libs.room.compiler)
    testImplementation(libs.junit)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
