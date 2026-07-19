plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

android {
  namespace = "com.premkumar.jiwtracker"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.premkumar.jiwtracker"
    minSdk = 26
    targetSdk = 36
    versionCode = 5
    versionName = (project.findProperty("versionName") as String?) ?: "2.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  flavorDimensions += "provider"
  productFlavors {
    create("standard") {
      dimension = "provider"
      isDefault = true
    }
    create("fdroid") {
      dimension = "provider"
    }
  }

  val keystorePathFromEnv = System.getenv("KEYSTORE_PATH")
  val storePasswordFromEnv = System.getenv("STORE_PASSWORD")
  val keyAliasFromEnv = System.getenv("KEY_ALIAS")
  val keyPasswordFromEnv = System.getenv("KEY_PASSWORD")
  val hasSigningEnv = keystorePathFromEnv != null && storePasswordFromEnv != null &&
    keyAliasFromEnv != null && keyPasswordFromEnv != null

  signingConfigs {
    create("release") {
      if (hasSigningEnv) {
        storeFile = file(keystorePathFromEnv)
        storePassword = storePasswordFromEnv
        keyAlias = keyAliasFromEnv
        keyPassword = keyPasswordFromEnv
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasSigningEnv) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Dynamic artifact naming: jiw-tracker-v<versionName>.apk / .aab
val versionNameValue = (project.findProperty("versionName") as String?) ?: "2.1.0"

fun renameApks(apkDir: java.io.File) {
  if (!apkDir.exists()) return
  apkDir.listFiles { f -> f.name.endsWith(".apk") && f.name != "jiw-tracker-v${versionNameValue}.apk" }
    ?.forEach { apk ->
      apk.renameTo(java.io.File(apkDir, "jiw-tracker-v${versionNameValue}.apk"))
    }
}

fun renameBundles(bundleDir: java.io.File) {
  if (!bundleDir.exists()) return
  bundleDir.listFiles { f -> f.name.endsWith(".aab") && f.name != "jiw-tracker-v${versionNameValue}.aab" }
    ?.forEach { aab ->
      aab.renameTo(java.io.File(bundleDir, "jiw-tracker-v${versionNameValue}.aab"))
    }
}

afterEvaluate {
  tasks.matching { it.name.startsWith("assemble") && it.name.endsWith("Release") }
    .configureEach { task ->
      val flavor = task.name.removePrefix("assemble").removeSuffix("Release").lowercase()
      task.doLast {
        renameApks(layout.buildDirectory.dir("outputs/apk/${flavor}/release").get().asFile)
      }
    }

  tasks.matching { it.name.startsWith("bundle") && it.name.endsWith("Release") }
    .configureEach { task ->
      val flavor = task.name.removePrefix("bundle").removeSuffix("Release").lowercase()
      task.doLast {
        renameBundles(layout.buildDirectory.dir("outputs/bundle/${flavor}/release").get().asFile)
      }
    }
}

kotlin {
  compilerOptions {
    optIn.add("androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi")
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material3.windowsizeclass)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.health.connect)
  implementation(libs.androidx.work.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  "standardImplementation"(libs.play.services.location)
  implementation(libs.retrofit)
  implementation("androidx.browser:browser:1.8.0")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
