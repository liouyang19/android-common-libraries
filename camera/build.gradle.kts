plugins {
	alias(libs.plugins.taisau.android.library)
}

android {
	namespace = "com.taisau.android.common.camera"
}

dependencies {
	implementation(libs.kotlinx.coroutines.core)
	implementation(libs.kotlinx.coroutines.android)
	implementation(libs.androidx.lifecycle.runtime.ktx)
}

apply(from = rootProject.projectDir.resolve("gradle/publish-library.gradle.kts"))