plugins {
	alias(libs.plugins.taisau.android.library.compose)
}

android {
	namespace = "com.taisau.android.common.theme"
}

dependencies{
	implementation(libs.androidx.compose.material3)
}

