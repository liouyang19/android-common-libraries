plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlin.android)
}

android {
	namespace = "com.taisau.android.common.permission"
	compileSdk {
		version = release(36) {
			minorApiLevel = 1
		}
	}
	
	defaultConfig {
		minSdk = 26
		
		testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
		consumerProguardFiles("consumer-rules.pro")
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	kotlinOptions {
		jvmTarget = "11"
	}
	
}

dependencies {
	implementation(libs.appcompat.v7)
	testImplementation(libs.junit)
	androidTestImplementation(libs.espresso.core)
	androidTestImplementation(libs.runner)
}