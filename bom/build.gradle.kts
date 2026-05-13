plugins {
	`java-platform`
	`maven-publish`
}

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
			artifactId = "bom"
			version = rootProject.version.toString()
		}
	}
}
