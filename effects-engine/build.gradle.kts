plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android { namespace = "com.animationstudio.effects"; compileSdk = libs.versions.compileSdk.get().toInt(); defaultConfig { minSdk = libs.versions.minSdk.get().toInt() } }
dependencies { api(project(":core")); implementation(libs.androidx.core.ktx); testImplementation(libs.junit) }
