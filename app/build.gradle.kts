plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingFile = System.getenv("GEV_KEYSTORE_FILE")
val hasSigning = !signingFile.isNullOrBlank()

android {
    namespace = "com.godseyeview"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.godseyeview"
        minSdk = 31
        targetSdk = 35
        // CI assigns versionCode (monotonic across both channels) and
        // versionName (semver; betas are <target>-beta.N, plan Q38).
        // Feature branches never set these.
        versionCode = System.getenv("GEV_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("GEV_VERSION_NAME") ?: "1.0.0-beta.1"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(signingFile)
                storePassword = System.getenv("GEV_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GEV_KEY_ALIAS")
                keyPassword = System.getenv("GEV_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core-globe"))
    implementation(project(":core-ui"))
    implementation(project(":layer-api"))
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    testImplementation("junit:junit:4.13.2")
}
