plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android { namespace = "com.animationstudio.importer"; compileSdk = libs.versions.compileSdk.get().toInt(); defaultConfig { minSdk = libs.versions.minSdk.get().toInt() } }
dependencies { api(project(":core")); implementation(project(":project-manager")); implementation(libs.androidx.core.ktx); implementation(libs.kotlinx.coroutines.android) }
