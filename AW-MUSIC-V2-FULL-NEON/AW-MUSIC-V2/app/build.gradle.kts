plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ir.alirezaweb.awmusic"
    compileSdk = 35
    defaultConfig {
        applicationId = "ir.alirezaweb.awmusic"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "2.1"
    }
}
