plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android { namespace = "com.animationstudio.settings"; compileSdk = libs.versions.compileSdk.get().toInt(); defaultConfig { minSdk = libs.versions.minSdk.get().toInt() } }
dependencies { implementation(libs.androidx.core.ktx); implementation(libs.datastore.preferences); implementation(libs.kotlinx.coroutines.android) }
