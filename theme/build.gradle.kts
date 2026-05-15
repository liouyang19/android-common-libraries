plugins {
	alias(libs.plugins.taisau.android.library.compose)
	id("maven-publish")
}

android {
	namespace = "com.taisau.android.common.theme"

	publishing {
		singleVariant("release") {
			withSourcesJar()
			withJavadocJar()
		}
	}
}

dependencies {
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.core.splashscreen)
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

