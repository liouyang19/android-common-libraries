plugins {
	alias(libs.plugins.taisau.android.library.compose)
	alias(libs.plugins.kotlin.dokka)
	id("maven-publish")
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

val dokkaJavadocJar by tasks.registering(Jar::class) {
	dependsOn(tasks.dokkaGenerateHtml)
	from(layout.buildDirectory.dir("dokka/html"))
	archiveClassifier.set("javadoc")
}

afterEvaluate {
	publishing {
		publications {
			create<MavenPublication>("release") {
				from(components["release"])
				artifact(dokkaJavadocJar)

				groupId = "com.github.liouyang19.android-common-libraries"
				artifactId = "theme"
				version = versionNameFromTags
			}
		}
	}
}

dokka {
	dokkaSourceSets.configureEach {
		includes.from("Module.md")
	}
}

dependencies {
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.core.splashscreen)
}

