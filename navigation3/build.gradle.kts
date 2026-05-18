import com.android.tools.r8.internal.im

plugins {
	alias(libs.plugins.taisau.android.library.compose)
	alias(libs.plugins.kotlin.serialization)
	`maven-publish`
}

android {
	namespace = "com.taisau.android.common.navigation3"

	publishing {
		singleVariant("release") {
			withSourcesJar()
		}
	}
}

dependencies{
	api(libs.androidx.navigation3.runtime)
	implementation(libs.androidx.savedstate.compose)
	implementation(libs.androidx.lifecycle.viewmodel.navigation3)
	testImplementation(libs.truth)
	androidTestImplementation(libs.androidx.compose.ui.test)
	androidTestImplementation(libs.androidx.test.ext)
	androidTestImplementation(libs.androidx.compose.ui.testManifest)
	androidTestImplementation(libs.androidx.lifecycle.viewmodel.testing)
	androidTestImplementation(libs.truth)

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


