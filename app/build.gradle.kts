import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
  val f = rootProject.file("local.properties")
  if (f.exists()) f.inputStream().use { load(it) }
}

fun secretProp(name: String, default: String = ""): String =
  System.getenv(name)?.takeIf { it.isNotBlank() }
    ?: localProps.getProperty(name)?.takeIf { it.isNotBlank() }
    ?: default

android {
  namespace = "com.scaleflow.leads"
  compileSdk = 35
  defaultConfig {
    applicationId = "com.scaleflow.leads"
    minSdk = 33
    targetSdk = 35
    versionCode = 9
    versionName = "1.5.3"

    val scriptUrl = secretProp("APPS_SCRIPT_URL", "")
    buildConfigField("String", "DEFAULT_SCRIPT_URL", "\"$scriptUrl\"")
  }

  signingConfigs {
    create("release") {
      val ksFile = rootProject.file("release-keystore.jks")
      if (ksFile.exists()) {
        storeFile = ksFile
        storePassword = secretProp("KEYSTORE_PASSWORD")
        keyAlias = secretProp("KEY_ALIAS", "scaleflow")
        keyPassword = secretProp("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val ksFile = rootProject.file("release-keystore.jks")
      signingConfig = if (ksFile.exists()) {
        signingConfigs.getByName("release")
      } else {
        signingConfigs.getByName("debug")
      }
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
  implementation(libs.androidx.compose.material.icons.extended)

  // JSON Serialization
  implementation(libs.kotlinx.serialization.json)

  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Tests
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(composeBom)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}
