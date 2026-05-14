plugins {
	alias(libs.plugins.taisau.android.library.compose)
	`maven-publish`
}

apply(from = "../gradle/git-tag-version.gradle.kts")
val versionNameFromTags: String by extra

android {
	namespace = "com.taisau.android.common.theme"

	publishing {
		singleVariant("release") {
			withSourcesJar()
		}
	}
}

afterEvaluate {
	publishing {
		publications {
			create<MavenPublication>("release") {
				from(components["release"])
				groupId = rootProject.group.toString()
				artifactId = "theme"
				version = versionNameFromTags
			}
		}
	}
}

dependencies {
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.core.splashscreen)
}

