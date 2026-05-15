plugins {
    alias(libs.plugins.taisau.android.library.compose)
    `maven-publish`
}

android {
    namespace = "com.taisau.android.common.permission"

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
   implementation(libs.accompanist.permissionsaccompanist.permissions)
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
