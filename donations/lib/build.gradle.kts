plugins {
  id("signal-library")
  id("kotlin-parcelize")
}

android {
  namespace = "org.signal.donations"

  buildFeatures {
    buildConfig = true
  }

  flavorDimensions += "license"

  productFlavors {
    create("gms") {
      dimension = "license"
      isDefault = true
    }

    create("foss") {
      dimension = "license"
    }
  }
}

dependencies {
  implementation(project(":core-util"))

  implementation(libs.kotlin.reflect)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.jackson.core)

  testImplementation(testLibs.robolectric.robolectric) {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
  }

  // SW: our foss dimension still uses fcm
  "gmsApi"(libs.google.play.services.wallet)
  "fossApi"(libs.google.play.services.wallet) {
    exclude(group = "com.google.android.gms", module = "play-services-maps")
  }
  //"fossApi"(project(":libfakegms"))
  api(libs.square.okhttp3)
}
