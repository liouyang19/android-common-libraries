plugins {
    alias(libs.plugins.taisau.android.library)
    `maven-publish`
}

android {
    namespace = "com.taisau.android.common.utils.android"

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core)
	implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
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
