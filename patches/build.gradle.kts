group = "app.yuubinnkyoku.nicovideo"

patches {
    about {
        name = "yuubinnkyoku niconico patches"
        description = "Video playback patches for the niconico Android application"
        source = "https://github.com/yuubinnkyoku/morphe-nicovideo-patch"
        author = "yuubinnkyoku"
        contact = "https://github.com/yuubinnkyoku/morphe-nicovideo-patch/issues"
        website = "https://github.com/yuubinnkyoku/morphe-nicovideo-patch"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
