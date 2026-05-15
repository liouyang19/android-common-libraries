plugins {
	`java-platform`
	`maven-publish`
}

javaPlatform {
	allowDependencies()
}

val versionNameFromTags: String by extra

dependencies {
	constraints {
		api("com.github.liouyang19.android-common-libraries:theme:${versionNameFromTags}")
		api("com.github.liouyang19.android-common-libraries:camera:${versionNameFromTags}")
		api("com.github.liouyang19.android-common-libraries:permission:${versionNameFromTags}")
		api("com.github.liouyang19.android-common-libraries:upgrade:${versionNameFromTags}")
		api("com.github.liouyang19.android-common-libraries:download:${versionNameFromTags}")
		api("com.github.liouyang19.android-common-libraries:network:${versionNameFromTags}")
	}
}

publishing {
	publications {
		register<MavenPublication>("bom") {
			from(components["javaPlatform"])
			groupId = rootProject.group.toString()
			artifactId = "${rootProject.name}-${project.name}"
			version = versionNameFromTags
		}
	}
}
