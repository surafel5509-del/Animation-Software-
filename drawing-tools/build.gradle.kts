plugins { alias(libs.plugins.kotlin.jvm) }
dependencies { api(project(":core")); implementation(project(":vector-engine")); testImplementation(libs.junit) }
kotlin { jvmToolchain(17) }
