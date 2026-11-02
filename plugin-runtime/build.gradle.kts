plugins { id("com.android.library") }
android {
    namespace = "dev.amenhancer.plugin.runtime"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    api(project(":plugin-api"))
    implementation(project(":hook-runtime"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
tasks.withType<Test>().configureEach {
    providers.gradleProperty("pluginFixture").orNull?.let {
        systemProperty("pluginFixture", it)
        inputs.file(it).withPropertyName("pluginFixture").withPathSensitivity(PathSensitivity.NONE)
    }
    providers.gradleProperty("externalMediaFixture").orNull?.let {
        systemProperty("externalMediaFixture", it)
        inputs.file(it).withPropertyName("externalMediaFixture").withPathSensitivity(PathSensitivity.NONE)
    }
}

tasks.withType<Test>().configureEach {
    providers.gradleProperty("lyricsFixture").orNull?.let {
        systemProperty("lyricsFixture", it)
        inputs.file(it).withPropertyName("lyricsFixture").withPathSensitivity(PathSensitivity.NONE)
    }
}
