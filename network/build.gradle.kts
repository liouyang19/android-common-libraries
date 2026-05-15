plugins {
    alias(libs.plugins.taisau.android.library)
    id("maven-publish")
}

android {
    namespace = "com.taisau.android.common.network"

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
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
android {
    namespace = "com.taisau.android.common.network"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}


