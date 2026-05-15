plugins {
	`java-platform`
}

javaPlatform {
	allowDependencies()
}

dependencies {
	constraints {
		api(project(":theme"))
		api(project(":camera"))
		api(project(":permission"))
		api(project(":upgrade"))
	}
}
