plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

// The release key: the workflow writes release.keystore from the GitHub secret KEYSTORE_B64.
// Without it the build still works, signed with a temporary debug key (fine for testing,
// but an APK signed that way cannot be updated over later ones signed with the real key).
val releaseKeystore = File(rootDir, "release.keystore")

android {
    namespace = "com.candyai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.candyai"
        minSdk = 26
        targetSdk = 35
        versionCode = 48
        versionName = "0.48"
        // Address of the Candy server: one line in api-url.txt. It can also be changed inside the app.
        val apiUrl = File(rootDir, "api-url.txt").takeIf { it.exists() }
            ?.readLines()?.firstOrNull()?.trim().orEmpty().replace("\"", "")
        // Repository the app checks for newer releases (the build can override it with -PupdateRepo=owner/name)
        buildConfigField("String", "UPDATE_REPO", "\"${project.findProperty("updateRepo") ?: "xt7h34/Candy-AI"}\"")
        buildConfigField("String", "SERVER_URL", "\"$apiUrl\"")
        // Web application client ID (NOT the Android one): the ID token's audience, checked by the server
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"1049344857995-irthfor4kq2u8p6dp4ltq6afkbeqejfr.apps.googleusercontent.com\"")
    }

    signingConfigs {
        if (releaseKeystore.exists()) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (releaseKeystore.exists()) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Sign in with Google
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}
