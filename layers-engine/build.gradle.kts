plugins { alias(libs.plugins.kotlin.jvm) }
dependencies { api(project(":core")); testImplementation(libs.junit) }
kotlin { jvmToolchain(17) }
