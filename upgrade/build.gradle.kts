plugins {
    alias(libs.plugins.taisau.android.library)
    `maven-publish`
}

android {
    namespace = "com.taisau.android.common.upgrade"

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
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
