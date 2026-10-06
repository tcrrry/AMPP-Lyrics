plugins { java }
val sdk = providers.gradleProperty("amppSdk").orElse("lib/ampp-plugin-api-v1.jar")
val androidSdk = providers.gradleProperty("androidSdk").orElse(providers.environmentVariable("ANDROID_HOME"))
val androidJar = listOf("android-37.0", "android-37").map { file("${androidSdk.get()}/platforms/$it/android.jar") }
    .firstOrNull { it.isFile } ?: error("Install Android platform 37")
dependencies { compileOnly(files(sdk.get(), androidJar)) }
java { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
val dexDir = layout.buildDirectory.dir("dex")
val dex = tasks.register<JavaExec>("dex") {
    dependsOn(tasks.jar)
    classpath = files("${androidSdk.get()}/build-tools/37.0.0/lib/d8.jar")
    mainClass = "com.android.tools.r8.D8"
    doFirst { dexDir.get().asFile.mkdirs() }
    args("--release", "--min-api", "26", "--lib", androidJar.absolutePath,
        "--classpath", file(sdk.get()).absolutePath, "--output", dexDir.get().asFile.absolutePath,
        tasks.jar.get().archiveFile.get().asFile.absolutePath)
}
val codeJar = tasks.register<Zip>("codeJar") {
    dependsOn(dex)
    from(dexDir) { include("classes*.dex") }
    archiveFileName = "code.jar"
    destinationDirectory = layout.buildDirectory.dir("plugin-code")
}
tasks.register<Zip>("pluginZip") {
    dependsOn(codeJar)
    from(codeJar.flatMap { it.archiveFile })
    from("plugin.json")
    from("assets") { into("assets") }
    archiveFileName = "Tcrrry-Lyrics-Plugin-0.1.0.zip"
    destinationDirectory = layout.buildDirectory.dir("dist")
}

val regression = tasks.register<JavaExec>("regression") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "com.tcrrry.lyrics.plugin.Regression"
}
tasks.named("check") { dependsOn(regression) }
// Plain Java assertions run in the regression task, with no test framework dependency.
tasks.test { enabled = false }
