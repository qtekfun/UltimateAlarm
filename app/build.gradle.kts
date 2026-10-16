import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.licensee)
}

/**
 * The app version lives in one place, `appVersion` in gradle.properties: MAJOR.MINOR.PATCH, optionally `-rcN` or `-spike`.
 * The version code is derived from it, so it never depends on dates or the machine:
 * (MAJOR*10000 + MINOR*100 + PATCH) * 100 + N for `-rcN`, 1 for `-spike` and 99 for a final release, which therefore
 * sorts after its pre-releases. Keep in sync with scripts/version-code.sh.
 */
val appVersion = providers.gradleProperty("appVersion").get()

fun versionCodeOf(version: String): Int {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)(?:-(?:rc\.?(\d+)|(spike)))?""").matchEntire(version)
        ?: error("appVersion must be MAJOR.MINOR.PATCH, MAJOR.MINOR.PATCH-rcN or MAJOR.MINOR.PATCH-spike: $version")
    val (major, minor, patch, rc, spike) = match.destructured
    require(minor.toInt() < 100 && patch.toInt() < 100 && (rc.isEmpty() || rc.toInt() in 1..98))
    val base = major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
    val suffix = when {
        rc.isNotEmpty() -> rc.toInt()
        spike.isNotEmpty() -> 1
        else -> 99
    }
    return base * 100 + suffix
}

/** Release signing from the environment (release.yml maps the RELEASE_* secrets here); without it the build is debug-signed. */
val releaseKeystore: String? = System.getenv("UG_KEYSTORE_FILE")

android {
    namespace = "com.qtekfun.ultimatealarm"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.qtekfun.ultimatealarm"
        minSdk = 31
        targetSdk = 37
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("UG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UG_KEY_ALIAS")
                keyPassword = System.getenv("UG_KEY_PASSWORD")
            }
        }
    }

    // Reproducible builds (F-Droid): no Google-encrypted dependency blob in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        debug {
            // Lets a debug build live next to a release build (different signing key).
            applicationIdSuffix = ".debug"
        }
        release {
            // Without a release key the APK is signed with the debug key so it stays installable.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            // The git commit is not part of the APK: a build from a source tarball must match.
            vcsInfo.include = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }

    // 03-ci-release.md: one APK per ABI plus a universal one, for release builds only (debug and CI keep a single APK).
    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources {
            // Debug probes of kotlinx.coroutines, only read by the debugger agent.
            excludes += "DebugProbesKt.bin"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Only free licenses may ship in the APK (F-Droid). Anything else fails the build.
licensee {
    allow("Apache-2.0")
    allow("MIT")
    allow("BSD-3-Clause")
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:scheduling"))
    implementation(project(":core:data"))
    implementation(project(":feature:alarms"))
    implementation(project(":feature:ringing"))
    implementation(project(":feature:holidays"))
    implementation(project(":feature:sounds"))
    implementation(project(":feature:clock"))
    implementation(project(":feature:backup"))
    implementation(project(":feature:onboarding"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
