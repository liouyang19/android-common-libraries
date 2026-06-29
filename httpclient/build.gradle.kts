plugins {
    alias(libs.plugins.taisau.android.library)
    `maven-publish`
}

android {
    namespace = "com.taisau.android.http.client"

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(libs.retrofit.core)
    api(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.retrofit.converter.kotlin.serialization)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.logging)
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
