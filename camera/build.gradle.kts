plugins {
	alias(libs.plugins.taisau.android.library)
	id("maven-publish")
}

android {
	namespace = "com.taisau.android.common.camera"

	publishing {
		singleVariant("release") {
			withSourcesJar()
			withJavadocJar()
		}
	}
}

afterEvaluate {
	publishing {
		publications {
			create<MavenPublication>("release") {
				from(components["release"])

				groupId = "com.github.liouyang19.android-common-libraries"
				artifactId = "camera"
				version = "0.1.0"
			}
		}
	}
}

dependencies {
	implementation(libs.kotlinx.coroutines.core)
	implementation(libs.kotlinx.coroutines.android)
	implementation(libs.androidx.lifecycle.runtime.ktx)
}