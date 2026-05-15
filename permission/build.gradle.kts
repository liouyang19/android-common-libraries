plugins {
    alias(libs.plugins.taisau.android.library.compose)
}

android {
    namespace = "com.taisau.android.common.permission"
}

dependencies {
   implementation(libs.accompanist.permissionsaccompanist.permissions)
}

apply(from = rootProject.projectDir.resolve("gradle/publish-library.gradle.kts"))
