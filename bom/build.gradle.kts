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
	}
}
