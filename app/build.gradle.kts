import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

// Release signing material is supplied by the build environment and is never
// committed to this repository. Define these environment variables locally when
// producing an official signed build; debug builds need none of them.
val releaseKeystore = System.getenv("MIMORA_KEYSTORE")?.let(::File) ?: File("release.jks")
val releaseStorePassword = System.getenv("MIMORA_KEYSTORE_PASSWORD").orEmpty()
val releaseKeyAlias = System.getenv("MIMORA_KEY_ALIAS") ?: "release"

android {
  namespace = "com.mimora.app"
  compileSdk = 37
  defaultConfig {
    applicationId = "com.mimora.app"
    minSdk = 26
    targetSdk = 37
    versionCode = 9
    versionName = "1.8.0"
  }

  signingConfigs {
    create("release") {
      storeFile = releaseKeystore
      storePassword = releaseStorePassword
      keyAlias = releaseKeyAlias
      // PKCS12 derives the key password from the store password.
      keyPassword = releaseStorePassword
    }
  }

  buildTypes {
    debug {
      // Tamper check is skipped in debug builds (different signing key).
      buildConfigField("String", "EXPECTED_CERT_SHA256", "\"\"")
    }
    release {
      // R8 shrinks, optimizes and obfuscates the release APK so decompilation
      // does not reveal readable application logic or class/method names.
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.getByName("release")
      buildConfigField("String", "EXPECTED_CERT_SHA256", "\"55:E0:E1:96:1A:B9:B9:CA:C1:42:CC:5E:6D:ED:8D:3D:88:3C:6C:E0:A3:55:7E:2E:E4:25:5B:A2:6D:59:B9:BD\"")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }

  lint {
    abortOnError = false
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // DataStore Preferences
  implementation("androidx.datastore:datastore-preferences:1.1.2")

  // WorkManager
  implementation("androidx.work:work-runtime-ktx:2.10.0")

  // Security Crypto
  implementation("androidx.security:security-crypto:1.1.0-alpha06")

  // Fingerprint / biometric unlock
  implementation("androidx.biometric:biometric:1.1.0")

  // Google Sign-In / Drive authorization
  implementation("com.google.android.gms:play-services-auth:22.0.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
}
