plugins {
	id("com.android.library")
}

android {
	namespace = "com.taisau.android.common.download"
	compileSdk {
		version = release(36) {
			minorApiLevel = 1
		}
	}
	
	defaultConfig {
		minSdk = 26
		
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		consumerProguardFiles("consumer-rules.pro")
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	
}

dependencies {
	implementation("androidx.appcompat:appcompat:1.7.1")
	implementation("androidx.core:core-ktx:1.18.0")
	implementation("com.google.android.material:material:1.13.0")
	testImplementation("junit:junit:4.14-SNAPSHOT")
	androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
	androidTestImplementation("androidx.test.ext:junit:1.3.0")
}