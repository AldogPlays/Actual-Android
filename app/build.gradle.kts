plugins { id("com.android.application") }
android {
    namespace = "app.actualbudget.wrapper"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig { applicationId = "app.actualbudget.wrapper"; minSdk = 29; targetSdk = 35; versionCode = 3; versionName = "1.0.1"
        ndk { abiFilters += (project.findProperty("actualAbis") as? String ?: "arm64-v8a").split(",") }
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    packaging { jniLibs { useLegacyPackaging = false } }
}
dependencies {
    implementation("org.mozilla.geckoview:geckoview:158.0.20261007201254")
    testImplementation("junit:junit:4.13.2")
}
