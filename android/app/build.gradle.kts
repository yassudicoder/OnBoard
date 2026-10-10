// Build file for the app module (the actual Android app).
import java.util.Properties

plugins {
    // The Android Gradle Plugin (AGP). Its version is in gradle/libs.versions.toml.
    alias(libs.plugins.android.application)
}

// -------------------------------------------------------------------------------------------------
// Per-laptop settings and API keys come from android/local.properties.
//
// THIS IS THE PATTERN FOR EVERY ANDROID KEY (now and in later phases):
//   1. Each teammate writes the key into android/local.properties. That file is git-ignored, so keys
//      never reach GitHub. The committed file local.properties.example lists the names to fill in.
//   2. This script reads the value below.
//   3. The value reaches the app in one of two ways:
//        - manifestPlaceholders["NAME"]  for SDKs that read their key from AndroidManifest.xml
//                                        (the Google Maps SDK does this), and
//        - buildConfigField(...)         for plain settings our Java code needs (BuildConfig.NAME).
//      For a secret key, put only a yes/no flag (HAS_..._KEY) into BuildConfig, never the key itself.
//   4. A missing or blank key is NOT an error: the app then uses the MOCK (offline) version of that
//      feature, so the demo always works with zero keys.
// After changing local.properties, rebuild the app (or "Sync Project with Gradle Files" in Android Studio).
// -------------------------------------------------------------------------------------------------
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

// Where the backend runs. 10.0.2.2 is how the Android emulator reaches the laptop it runs on.
// Keep the trailing "/": Retrofit (Phase 1) needs it. (AppConfig.java adds it if someone forgets.)
val backendBaseUrl: String = localProperties.getProperty("BACKEND_BASE_URL", "").trim()
    .ifEmpty { "http://10.0.2.2:8080/" }

// Parked features (CLAUDE.md "Parked"): the old trip-timeline screens. Off unless android/local.properties says
// feature.tripTimeline=true (or the build gets -Pfeature.tripTimeline=true). Off = the app has no link to them;
// their code and tests stay and keep passing.
val featureTripTimeline: Boolean = (localProperties.getProperty("feature.tripTimeline")
    ?: (project.findProperty("feature.tripTimeline") as String?) ?: "false").trim().toBoolean()

// Google Maps key (used from Phase 7). The "is the key set?" rule lives HERE, and only here:
// missing, empty or only spaces = not set -> HAS_MAPS_KEY is false (below) -> the app uses
// mock/offline maps (AppConfig.mapsMode() says "MOCK").
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY", "").trim()

android {
    namespace = "com.safarsaathi.app"   // Java package of the generated R and BuildConfig classes
    compileSdk = 37                     // Android API the code is compiled against (latest stable)

    defaultConfig {
        applicationId = "com.safarsaathi.app"   // the app's unique id on a phone / in the Play Store
        minSdk = 26          // Android 8.0: oldest phone we support (also gives us java.time everywhere)
        targetSdk = 37       // the newest Android rules the app follows (e.g. edge-to-edge screens)
        versionCode = 1
        versionName = "0.1.0"

        // Runs the tests in src/androidTest on a phone or emulator.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // local.properties -> app (see the box at the top of this file).
        // buildConfigField writes Java source into the generated BuildConfig class, so a String needs quotes.
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
        // Parked trip-timeline screens: linked from Profile only when true (see featureTripTimeline above).
        buildConfigField("boolean", "FEATURE_TRIP_TIMELINE", featureTripTimeline.toString())
        // Only "is there a key?" goes into BuildConfig, never the key itself.
        buildConfigField("boolean", "HAS_MAPS_KEY", mapsApiKey.isNotEmpty().toString())
        // The key itself only goes into AndroidManifest.xml (${MAPS_API_KEY}), where the Maps SDK reads it.
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey

        // Room (the offline database) writes a JSON description of every database version into app/schemas/.
        // Commit those files: they show in a code review exactly how the tables changed, and later phases
        // need them to write database migrations.
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    buildTypes {
        release {
            // Code shrinking (R8) stays off for the hackathon: simpler debugging, fewer surprises.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // Our Java code may use Java 17 language features.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Room's exported schemas (app/schemas) as test assets: the Document Vault's migration test builds the OLD
    // database version from them (VaultMigrationTest).
    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    buildFeatures {
        // AGP 9 switches BuildConfig off by default; we need it for BACKEND_BASE_URL and HAS_MAPS_KEY.
        buildConfig = true
        // View binding: for each layout file Android generates a class with one field per view id
        // (activity_main.xml -> ActivityMainBinding), so we never need findViewById().
        viewBinding = true
    }
}

dependencies {
    // App code
    implementation(libs.androidx.appcompat)              // AppCompatActivity, works the same on old and new Android
    implementation(libs.material)                        // Material 3 widgets: toolbar, bottom navigation, ...
    implementation(libs.androidx.constraintlayout)       // flexible screen layouts
    implementation(libs.androidx.navigation.fragment)    // NavHostFragment: swaps the tab screens
    implementation(libs.androidx.navigation.ui)          // connects the bottom bar and toolbar to navigation
    implementation(libs.androidx.recyclerview)           // scrolling lists (the trip timeline)
    implementation(libs.androidx.swiperefreshlayout)     // pull down to refresh
    implementation(libs.androidx.lifecycle.viewmodel)    // ViewModel: screen data that survives screen rotation
    implementation(libs.androidx.lifecycle.livedata)     // LiveData: data the screen observes and redraws from

    // Offline storage: Room is a friendly layer over the phone's SQLite database.
    // The compiler generates the database code from our @Entity/@Dao classes while the app builds.
    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)

    // Wallet: reads the barcode on a boarding pass photo (PDF417, Aztec, QR) on the phone itself, with no internet.
    // The "bundled" model is inside the app, so it also works on emulators and phones without Google Play.
    implementation(libs.mlkit.barcode.scanning)
    // Wallet PDF: draws each boarding pass's barcode (as a QR code) into the downloaded ticket.
    implementation(libs.zxing.core)

    // Document Vault: the vault's own database file is encrypted with SQLCipher (Room still writes the SQL for us,
    // SQLCipher encrypts every page of the file). androidx.sqlite is the small interface layer Room and SQLCipher
    // both plug into; SQLCipher's README asks apps to add it next to SQLCipher.
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)
    // Document Vault: the fingerprint / face / device PIN prompt (BiometricPrompt) that unlocks the vault.
    implementation(libs.androidx.biometric)
    // Document Vault V2, adding documents: Google's document scanner (screen + models from Play services), text
    // recognition with the model inside the app (passport MRZ, offline), and CameraX for the vault's own camera
    // screen when the Google scanner cannot run.
    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Talking to our backend: Retrofit turns SafarSaathiApi into HTTP calls, Gson reads the JSON,
    // OkHttp does the actual networking (its logging interceptor prints requests in debug builds).
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)

    // Tests that run on the laptop (src/test) - fast, no emulator needed
    testImplementation(libs.junit)

    // Tests that run on a phone or emulator (src/androidTest)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.contrib)
    androidTestImplementation(libs.hamcrest.library)     // same Hamcrest version when compiling and running the tests
}
