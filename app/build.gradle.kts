plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun localProp(name: String): String? {
    val file = rootProject.file("local.properties")
    if (!file.isFile) return null
    for (raw in file.readLines()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) continue
        val split = line.indexOf('=')
        if (split <= 0) continue
        if (line.substring(0, split).trim() == name) {
            return line.substring(split + 1).trim().takeIf { it.isNotEmpty() }
        }
    }
    return null
}

android {
    namespace = "app.fieldwatch"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.fieldwatch"
        minSdk = 29
        targetSdk = 35
        versionCode = 27
        versionName = "1.1.17"
        vectorDrawables.useSupportLibrary = true
    }

    val releaseStore = localProp("FIELDWATCH_STORE_FILE")
    val releaseStorePassword = localProp("FIELDWATCH_STORE_PASSWORD")
    val releaseKeyAlias = localProp("FIELDWATCH_KEY_ALIAS")
    val releaseKeyPassword = localProp("FIELDWATCH_KEY_PASSWORD")
    val hasReleaseSigning =
        releaseStore != null &&
            releaseStorePassword != null &&
            releaseKeyAlias != null &&
            releaseKeyPassword != null &&
            file(releaseStore).isFile

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ""
            signingConfig = signingConfigs.getByName("debugConfig")
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    // FASE Tests: Robolectric necesita acceso a los recursos Android
    // (assets, strings, layouts) para shadow-ear el runtime. Sin esto,
    // cualquier test que use ApplicationProvider falla con
    // "Resources.NotFoundException" o "AssetManager not mocked".
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Test unitarios (JVM, sin device) -----------------------------
    testImplementation("junit:junit:4.13.2")
    // JSONObject real en el classpath de tests JVM; el android.jar trae
    // un stub que revienta cualquier test que use org.json directamente.
    testImplementation("org.json:json:20240303")
    // runTest + helpers de coroutines para testear TrainingStore.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Robolectric 4.14 es la primera versión que soporta compileSdk=35.
    // Versiones < 4.14 solo llegan hasta SDK 34 y fallan al arrancar el
    // RuntimeEnvironment en tests.
    testImplementation("org.robolectric:robolectric:4.14.1")
    // ApplicationProvider.getApplicationContext() para los tests que
    // necesitan un Context real (TrainingStore, ConfigStore, etc.).
    testImplementation("androidx.test:core:1.6.1")
}