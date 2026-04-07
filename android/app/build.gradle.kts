import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// --- Auto-incrementing version from version.properties ---
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) load(versionPropsFile.inputStream())
}
val appVersionCode = versionProps.getProperty("VERSION_CODE", "1").toInt()
val appVersionName = versionProps.getProperty("VERSION_NAME", "1.0.0")

// --- Release signing from key.properties ---
val signingPropsFile = rootProject.file("key.properties")
val signingProps = Properties().apply {
    if (signingPropsFile.exists()) load(signingPropsFile.inputStream())
}
val releaseStoreFile = signingProps.getProperty("storeFile")
val releaseStorePassword = signingProps.getProperty("storePassword")
val releaseKeyAlias = signingProps.getProperty("keyAlias")
val releaseKeyPassword = signingProps.getProperty("keyPassword")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

// Task to bump version before assembling release
tasks.register("bumpVersion") {
    doLast {
        val newCode = appVersionCode + 1
        val parts = appVersionName.split(".")
        val newPatch = (parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0) + 1
        val newName = "${parts[0]}.${parts[1]}.$newPatch"
        versionProps.setProperty("VERSION_CODE", newCode.toString())
        versionProps.setProperty("VERSION_NAME", newName)
        versionProps.store(versionPropsFile.outputStream(), null)
        println("Version bumped: $appVersionName ($appVersionCode) → $newName ($newCode)")
    }
}

tasks.matching { it.name.startsWith("assembleRelease") }.configureEach {
    dependsOn("bumpVersion")
}

android {
    namespace = "com.tickideas.appstore"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tickideas.appstore"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "API_BASE_URL", "\"https://apps-api.tikd.dev\"")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Image loading
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
