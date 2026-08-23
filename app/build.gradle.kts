import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
}

// Secrets live in local.properties (gitignored), never in source or source control.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun secret(key: String): String = localProperties.getProperty(key).orEmpty()

android {
    namespace = "com.techfix.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.techfix.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Block 3 seeding signs in as an existing ADMIN account so it passes the
        // Firestore rules. Passed to `am instrument` at run time from
        // local.properties, so the password is never compiled into any APK.
        testInstrumentationRunnerArguments["seedAdminEmail"] = secret("SEED_ADMIN_EMAIL")
        testInstrumentationRunnerArguments["seedAdminPassword"] = secret("SEED_ADMIN_PASSWORD")

        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")

        // Maps SDK key. Read from local.properties (gitignored) and injected two
        // ways: into the manifest's com.google.android.geo.API_KEY meta-data,
        // which is where the Maps SDK actually reads it from, and into
        // BuildConfig so code can fail loudly with a clear message when it is
        // missing instead of rendering a silently blank map. Never hardcoded.
        buildConfigField("String", "MAPS_API_KEY", "\"${secret("MAPS_API_KEY")}\"")
        manifestPlaceholders["MAPS_API_KEY"] = secret("MAPS_API_KEY")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Firebase — Auth (email + Google) and Firestore, including technicians.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Google Sign-In via Credential Manager (Block 2)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Room — service catalog cache + draft repair request only
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Supabase — spare parts, spare-part stock, and repair-image Storage.
    // The original technician rows remain untouched as a migration archive.
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.android)

    // Camera + image loading
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // Compile-classpath fix: camera-core's ProcessCameraProvider.getInstance()
    // returns a ListenableFuture. Firestore already pulls the real Guava
    // onto the runtime classpath (hence version-pinned to match), but not
    // the compile classpath, so ListenableFuture is otherwise unresolvable
    // here at compile time (Guava's own module metadata substitutes an
    // empty stub for listenablefuture unless real Guava is also present).
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.guava)
    implementation(libs.coil.compose)

    // GPS + Maps. maps-compose is the Compose wrapper over play-services-maps;
    // the latter is declared explicitly rather than leaned on transitively so
    // the version is pinned here rather than resolved by maps-compose.
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
