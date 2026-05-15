plugins {
    alias(libs.plugins.taisau.android.library)
}

android {
    namespace = "com.taisau.android.common.network"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}

apply(from = rootProject.projectDir.resolve("gradle/publish-library.gradle.kts"))
