plugins {
    alias(libs.plugins.taisau.android.library)
}

android {
    namespace = "com.taisau.android.common.upgrade"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
}

apply(from = rootProject.projectDir.resolve("gradle/publish-library.gradle.kts"))
