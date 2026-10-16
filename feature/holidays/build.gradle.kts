plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.qtekfun.ultimatealarm.feature.holidays"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
}
