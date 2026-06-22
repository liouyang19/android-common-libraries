plugins {
    alias(libs.plugins.taisau.android.library)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

android {
    namespace = "com.taisau.android.common.rabbitmq"
    
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation("com.rabbitmq:amqp-client:5.18.0")
   // implementation(libs.amqp.client)
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