import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

val local = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
val hasFirebase = file("google-services.json").exists()
val customStore = local.getProperty("RELEASE_STORE_FILE")
val localStore = file("local-release.keystore")

android {
    namespace = "com.machine.newsapp"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.machine.newsapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "NEWS_API_KEY", quoted(local.getProperty("NEWS_API_KEY", "")))
        buildConfigField("boolean", "FCM_CONFIGURED", hasFirebase.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            storeFile = customStore?.let { rootProject.file(it) } ?: localStore
            storePassword = local.getProperty("RELEASE_STORE_PASSWORD", "newsapp-local")
            keyAlias = local.getProperty("RELEASE_KEY_ALIAS", "newsapp")
            keyPassword = local.getProperty("RELEASE_KEY_PASSWORD", "newsapp-local")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

// No fake Firebase project is needed to build or use the polling fallback.
tasks.matching { it.name.endsWith("GoogleServices") }.configureEach { enabled = hasFirebase }
val generateLocalSigningKey by tasks.registering(Exec::class) {
    onlyIf { customStore == null && !localStore.exists() }
    commandLine(
        "${System.getProperty("java.home")}/bin/keytool", "-genkeypair", "-noprompt",
        "-keystore", localStore.absolutePath, "-storepass", "newsapp-local",
        "-keypass", "newsapp-local", "-alias", "newsapp", "-keyalg", "RSA",
        "-keysize", "2048", "-validity", "10000", "-dname", "CN=News App Local Build"
    )
}
tasks.matching { it.name == "validateSigningRelease" }.configureEach { dependsOn(generateLocalSigningKey) }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.browser:browser:1.8.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    implementation("com.google.firebase:firebase-messaging")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
