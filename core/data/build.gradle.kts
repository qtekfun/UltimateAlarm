plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.qtekfun.ultimatealarm.core.data"
}

dependencies {
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
