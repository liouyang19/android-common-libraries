plugins {
    alias(libs.plugins.taisau.android.library.compose)
}

android {
    namespace = "com.taisau.android.common.permission"
}

dependencies {
   implementation(libs.accompanist.permissionsaccompanist.permissions)
}
