plugins {
	alias(libs.plugins.taisau.android.library.compose)
}

android {
	namespace = "com.taisau.android.common.theme"
}

dependencies {
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.core.splashscreen)
}

apply(from = rootProject.projectDir.resolve("gradle/publish-library.gradle.kts"))

