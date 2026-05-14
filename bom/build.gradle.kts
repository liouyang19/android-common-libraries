plugins {
	`java-platform`
	`maven-publish`
}
apply(from = "../gradle/git-tag-version.gradle.kts")
val versionNameFromTags: String by extra
javaPlatform {
	allowDependencies()
}

dependencies {
	constraints {
		api(project(":theme"))
	}
}

publishing {
	publications {
		create<MavenPublication>("bom") {
			from(components["javaPlatform"])

			groupId = rootProject.group.toString()
			artifactId = "android-common-libraries-bom"
			version = versionNameFromTags
		}
	}
}
