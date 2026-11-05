plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android { namespace = "com.animationstudio.audio"; compileSdk = libs.versions.compileSdk.get().toInt(); defaultConfig { minSdk = libs.versions.minSdk.get().toInt() } }
dependencies { api(project(":core")); implementation(libs.androidx.core.ktx); implementation(libs.kotlinx.coroutines.android); testImplementation(libs.junit) }
