plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.ucd.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ucd.player"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // yt-dlp/Python/ffmpeg pesan mucho: solo teléfonos de 64 bits (casi todos los actuales)
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    // youtubedl-android ejecuta binarios que van dentro de lib/: deben quedar extraídos
    packaging { jniLibs { useLegacyPackaging = true } }
    // Firma fija: así cada APK nuevo se instala ENCIMA del anterior sin perder listas ni ajustes
    signingConfigs {
        create("ucd") {
            storeFile = file("ucd.jks")
            storePassword = "ucdplayer"
            keyAlias = "ucd"
            keyPassword = "ucdplayer"
        }
    }
    buildTypes {
        // El workflow compila "debug". Se deja optimizado (no depurable + R8) porque un build
        // de depuración hace que Compose vaya notablemente más lento al hacer scroll.
        debug {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("ucd")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("ucd")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.17.0")

    val media3 = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    // Desenfoque real de fondo para el cristal (Haze)
    implementation("dev.chrisbanes.haze:haze:1.6.0")

    val room = "2.7.2"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // yt-dlp en Android (incluye Python y yt-dlp) + ffmpeg para unir video y audio
    val ytdl = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")
}
