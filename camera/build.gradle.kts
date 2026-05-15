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

dependencies {
	implementation(libs.kotlinx.coroutines.core)
	implementation(libs.kotlinx.coroutines.android)
	implementation(libs.androidx.lifecycle.runtime.ktx)
}

val versionNameFromTags: String by extra

afterEvaluate {
	publishing {
		publications {
			register<MavenPublication>("release") {
				from(components["release"])
				groupId = rootProject.group.toString()
				artifactId = project.name
				version = versionNameFromTags
			}
		}
	}
}