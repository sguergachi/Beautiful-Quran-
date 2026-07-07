plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/** CI exports unset secrets as empty strings; treat those as absent. */
fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.beautifulquran"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.beautifulquran"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Store keystore; not committed. Credentials can be overridden from
        // the environment (CI secrets) and default to the local values.
        create("release") {
            storeFile = rootProject.file("release.keystore")
            storePassword = env("RELEASE_KEYSTORE_PASSWORD") ?: "division"
            keyAlias = env("RELEASE_KEY_ALIAS") ?: "beautifulquran"
            keyPassword = env("RELEASE_KEY_PASSWORD") ?: "division"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sign with the store keystore when it exists; otherwise fall back
            // to the debug keystore so CI and fresh clones can still
            // assembleRelease. Note the two signatures cannot update over each
            // other on a device — store builds need the real keystore present.
            signingConfig = if (rootProject.file("release.keystore").exists()) {
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += "db"
        noCompress += "ttf"
        noCompress += "xz"
        noCompress += "part0"
        noCompress += "part1"
    }
    lint {
        // Media3's @UnstableApi opt-in trips lintVital on release builds; the
        // full lint task still reports it. CI ships release APKs, so keep
        // assembleRelease unblocked.
        checkReleaseBuilds = false
    }
}

val checkQuranDbAsset by tasks.registering {
    val dbAsset = layout.projectDirectory.file("src/main/assets/quran.db")
    doLast {
        if (!dbAsset.asFile.isFile) {
            throw GradleException(
                "Missing bundled Quran database: ${dbAsset.asFile}. " +
                    "Run `python3 tools/build_db.py` from the repo root before building locally.",
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(checkQuranDbAsset)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.xz)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
