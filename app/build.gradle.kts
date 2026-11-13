import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

val versionCodeOverride = providers.gradleProperty("versionCode").orNull?.toIntOrNull()
    ?: providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull()

val bulltrackAbis: List<String> = providers.gradleProperty("bulltrackAbis")
    .orNull
    ?.split(",")
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?.takeIf(List<String>::isNotEmpty)
    ?: listOf("arm64-v8a")

tasks.named("preBuild") {
    dependsOn(rootProject.tasks.named("fetchBulltrackNativeDeps"))
}

android {
    namespace = "com.darts.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    // Keep the app's strip task on the same NDK as bulltrack-core; AGP's default
    // NDK may be missing, which silently ships unstripped native libraries.
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "com.dartsapp.dartytime"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeOverride ?: 1
        versionName = "1.0.0"

        ndk {
            abiFilters += bulltrackAbis
            // Keep the AAB small; native symbol upload is not configured.
            debugSymbolLevel = "NONE"
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":bulltrack-core")) {
        // BullTrack only uses ListenableFuture from Guava; the full Guava jar is
        // unnecessary weight in the app. CameraX brings the tiny listenablefuture API.
        exclude(group = "com.google.guava", module = "guava")
    }

    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.concurrent:concurrent-futures:1.1.0")
    implementation("com.google.guava:listenablefuture:1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
