import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.publish.PublishingExtension

plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.android.library) apply false
	alias(libs.plugins.kotlin.compose) apply false
}

allprojects {
	tasks.withType<JavaCompile>().configureEach {
		sourceCompatibility = JavaVersion.VERSION_21.toString()
		targetCompatibility = JavaVersion.VERSION_21.toString()
	}
}

subprojects {
	apply(from = "${rootProject.projectDir}/gradle/git-tag-version.gradle.kts")
	plugins.apply("maven-publish")

	afterEvaluate {
		val versionNameFromTags: String by extra

		extensions.findByType<LibraryExtension>()?.publishing {
			singleVariant("release") {
				withSourcesJar()
				withJavadocJar()
			}
		}

		configure<PublishingExtension> {
			components.findByName("release")?.let { releaseComp ->
				publications {
					create<MavenPublication>("release") {
						from(releaseComp)
						groupId = rootProject.group.toString()
						artifactId = project.name
						version = versionNameFromTags
					}
				}
			}
			components.findByName("javaPlatform")?.let { javaPlatformComp ->
				publications {
					create<MavenPublication>("bom") {
						from(javaPlatformComp)
						groupId = rootProject.group.toString()
						artifactId = "${rootProject.name}-${project.name}"
						version = versionNameFromTags
					}
				}
			}
		}
	}
}