plugins { id("com.android.application") }
android {
    namespace = "com.tcrrry.vivo"
    compileSdk = 37
    // This compiler payload runs inside the verified 1609 host (target 36), never as its own APK.
    defaultConfig { applicationId = "com.tcrrry.vivo.compiler"; minSdk = 31; targetSdk = 36; versionCode = 16; versionName = "0.2-preview.3" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    compileOnly(project(":plugin-api"))
    testImplementation("junit:junit:4.13.2")
    testImplementation(project(":plugin-api"))
}
