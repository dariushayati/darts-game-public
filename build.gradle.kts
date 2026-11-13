import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.application") version "8.13.0" apply false
    id("com.android.library") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.0" apply false
}

// bulltrack-core reads these from rootProject.ext so its build stays independent of
// this app's Gradle configuration.
extra["compileSdkVersion"] = 36
extra["minSdkVersion"] = 26
extra["ndkVersion"] = "27.1.12297006"

// The engine is only used through the BullTrack wrapper, so arm64 is the default;
// pass -PbulltrackAbis=arm64-v8a,armeabi-v7a to build more.
val bulltrackAbis: List<String> = providers.gradleProperty("bulltrackAbis")
    .orNull
    ?.split(",")
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?.takeIf(List<String>::isNotEmpty)
    ?: listOf("arm64-v8a")

val fetchBulltrackNativeDeps by tasks.registering(Exec::class) {
    group = "build"
    description = "Download the pinned BullTrack native SDKs and YOLO model (checksummed)."
    workingDir = rootDir
    commandLine("bash", "scripts/fetch-bulltrack-deps.sh")
}

project(":bulltrack-core") {
    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            defaultConfig {
                ndk {
                    abiFilters.clear()
                    abiFilters.addAll(bulltrackAbis)
                }
                // Android 15+ devices may use 16 KB pages. NDK 27 needs this flag to
                // link libbulltrack.so (and its C++ runtime) with 16 KB alignment.
                externalNativeBuild {
                    cmake {
                        arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                    }
                }
            }
        }
    }
    // The module's own build script adds every ABI after the root config runs.
    // Apply the requested ABI list once more after evaluation so release/debug
    // builds only package the ABIs we actually ship.
    afterEvaluate {
        extensions.configure<LibraryExtension> {
            defaultConfig {
                ndk {
                    abiFilters.clear()
                    abiFilters.addAll(bulltrackAbis)
                }
            }
        }
    }
    // bulltrack-core sets Java 11 explicitly but does not set Kotlin's JVM target.
    plugins.withId("org.jetbrains.kotlin.android") {
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }
    }
    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(fetchBulltrackNativeDeps)
    }
}

tasks.register("coverage") {
    group = "verification"
    description = "Run all unit tests and generate JaCoCo coverage reports."
    dependsOn(
        ":core:jacocoTestReport",
        ":vision:jacocoTestReport",
        ":app:createDebugUnitTestCoverageReport",
    )
}
