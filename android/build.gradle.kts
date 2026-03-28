allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.gradle.LibraryExtension> {
            if (namespace == null) {
                namespace = "com.joshiminh.wallbase.${project.name.replace("-", "_")}"
            }
        }

        // Nuclear Option: Strip package attribute from manifest if present
        project.tasks.withType<com.android.build.gradle.tasks.ProcessLibraryManifest> {
            doFirst {
                val manifestFile = mainManifest.get().asFile
                if (manifestFile.exists()) {
                    val content = manifestFile.readText()
                    // Remove package="something" attribute
                    val newContent = content.replace(Regex("""package="[^"]+""""), "")
                    if (content != newContent) {
                        println("Stripping package attribute from ${project.name} manifest to satisfy AGP 8 requirements.")
                        manifestFile.writeText(newContent)
                    }
                }
            }
        }
    }
    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.gradle.BaseExtension> {
            if (namespace == null) {
                namespace = "com.joshiminh.wallbase.${project.name.replace("-", "_")}"
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
