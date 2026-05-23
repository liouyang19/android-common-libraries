plugins {
    alias(libs.plugins.taisau.android.library)
    `maven-publish`
}

android {
    namespace = "com.taisau.android.common.mqtt"

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    //implementation(libs.mqtt.client)
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
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
