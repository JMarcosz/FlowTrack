plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.flowtrack"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.flowtrack"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Solo arm64-v8a en debug — el dispositivo de desarrollo es uno solo.
            // Ahorra 10–20 MB de .so (PdfBox-Android, Firestore native) sin afectar release.
            ndk { abiFilters += listOf("arm64-v8a") }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("minifiedDebug") {
            initWith(getByName("debug"))
            isMinifyEnabled = true
            isShrinkResources = true
            matchingFallbacks += "release"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true   // allows Android Log, etc. in JVM unit tests
    }
    packaging {
        resources {
            // Conflictos comunes de Apache POI, PdfBox y librerías Apache Commons
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "mozilla/public-suffix-list.txt"

            // BouncyCastle post-quantum crypto (SIKE, Picnic) — arrastrado por poi-ooxml
            // para verificar firmas OOXML. CibaoXlsParser solo lee celdas financieras,
            // nunca verifica firmas digitales. Estos .properties suman ~8 MB sin uso.
            excludes += "org/bouncycastle/pqc/**"
            excludes += "org/bouncycastle/crypto/test/**"
            excludes += "font_metrics.properties"

            // POI office subsystems no usados — CibaoXlsParser solo lee celdas XLS/XLSX,
            // nunca abre presentaciones PowerPoint (HSLF), documentos Word (HWPF),
            // ni correos Outlook (HSMF). Los XML de shapes/tables suman ~1.2 MB sin uso.
            excludes += "org/apache/poi/hslf/**"
            excludes += "org/apache/poi/hwpf/**"
            excludes += "org/apache/poi/hsmf/**"
            excludes += "org/apache/poi/sl/draw/binding/presetShapeDefinitions.xml"
            excludes += "org/apache/poi/xwpf/usermodel/presetTableStyles.xml"
        }
    }
}

dependencies {

    // ── AndroidX Core ────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // ── Compose ──────────────────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    // ── Navigation ───────────────────────────────────────────────────────────
    implementation(libs.navigation.compose)

    // ── Hilt ─────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Coroutines ───────────────────────────────────────────────────────────
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    // ── Google Sign-In (Credential Manager) ──────────────────────────────────
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)

    // ── Parsers ───────────────────────────────────────────────────────────────
    implementation(libs.pdfbox.android)          // PDFs: BanReservas, Qik
    implementation(libs.poi)                     // XLS legacy HSSF: Cibao (.xls)
    implementation(libs.poi.ooxml)               // XLSX XSSF: Cibao (.xlsx)
    implementation(libs.opencsv)                 // CSV: Popular
    // fastexcel eliminado — exportación usa FileWriter CSV nativo

    // Vico eliminado — DonutChart usa Canvas nativo de Compose, Vico nunca se importó

    // coil eliminado — app solo usa drawables locales, sin AsyncImage

    // ── Local storage ─────────────────────────────────────────────────────────
    implementation(libs.datastore.preferences)

    // ── WorkManager (notificaciones programadas — Sprint 7) ───────────────────
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    // ── Glance (widgets home screen) ──────────────────────────────────────────
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // ── Firebase ──────────────────────────────────────────────────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    // Crashlytics requiere su Gradle plugin — se agrega en Sprint 9 (release)
    // implementation(libs.firebase.crashlytics)
    // firebase-analytics eliminado — no se llama logEvent en ningún lugar

    // ── Testing ───────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
