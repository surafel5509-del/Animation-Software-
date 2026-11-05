plugins { alias(libs.plugins.kotlin.jvm) }
dependencies { api(project(":core")); implementation(libs.kotlinx.serialization.json); testImplementation(libs.junit) }
kotlin { jvmToolchain(17) }
