plugins {
    alias(libs.plugins.taisau.android.library)
}

android {
    namespace = "com.taisau.android.common.download"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}

apply(from = rootProject.projectDir.resolve("gradle/publish-library.gradle.kts"))
