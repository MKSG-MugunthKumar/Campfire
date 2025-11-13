plugins {
  id("app.campfire.multiplatform")
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
  sourceSets {
    commonMain {
      dependencies {
        api(projects.core)
        api(libs.assertk)
        api(libs.kotlinx.coroutines.test)
      }
    }
  }
}
