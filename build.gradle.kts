// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
}

tasks.register("listFiles") {
    doLast {
        println("=== DEEP FILE SEARCH ===")
        val rootDirFile = layout.projectDirectory.asFile
        val startDir = rootDirFile.parentFile?.parentFile ?: rootDirFile
        println("Starting search from: ${startDir.absolutePath}")
        startDir.walkTopDown().forEach { file ->
            if (file.isFile && (file.name.endsWith(".png") || file.name.endsWith(".webp") || file.name.contains("attached") || file.name.contains("icon"))) {
                if (!file.absolutePath.contains("/build/") && !file.absolutePath.contains("/.gradle/")) {
                    println("${file.absolutePath} (${file.length()} bytes)")
                }
            }
        }
    }
}


