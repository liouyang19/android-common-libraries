plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.android.library) apply false
	alias(libs.plugins.kotlin.compose) apply false
	alias(libs.plugins.kotlin.serialization) apply false
	alias(libs.plugins.kotlin.ksp) apply false
	alias(libs.plugins.kotlin.android) apply false
	alias(libs.plugins.room) apply false
}

allprojects {
	tasks.withType<JavaCompile>().configureEach {
		sourceCompatibility = JavaVersion.VERSION_21.toString()
		targetCompatibility = JavaVersion.VERSION_21.toString()
	}
}

subprojects {
	apply(from = "${rootProject.projectDir}/gradle/git-tag-version.gradle.kts")
}