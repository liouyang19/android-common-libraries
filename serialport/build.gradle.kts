plugins {
    alias(libs.plugins.taisau.android.library)
    alias(libs.plugins.kotlin.android)
    id("cpp")
}

android {
    namespace = "com.taisau.android.common.serialport"
    externalNativeBuild{
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
}

dependencies{

}
