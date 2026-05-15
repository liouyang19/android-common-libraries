plugins {
    alias(libs.plugins.taisau.android.library.compose)
}

android {
    namespace = "com.taisau.android.common.upgrade"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
}
