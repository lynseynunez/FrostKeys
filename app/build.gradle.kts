plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.flexkeys"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.flexkeys"
        minSdk = 26
        targetSdk = 34
        // The release workflow passes the tag as the version; local and debug builds use these defaults.
        versionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (findProperty("appVersionName") as String?) ?: "1.0.0"
    }

    signingConfigs {
        // A fixed debug key checked into the repo, so every CI build can be installed over the
        // previous one. It only signs debug builds and protects nothing.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    signingConfigs {
        // Release signing comes from environment variables set by the release workflow, so the
        // real key is never stored in the repository.
        create("release") {
            val path = System.getenv("RELEASE_KEYSTORE_PATH")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without the release key (local builds) fall back to the debug key so the build still works.
            signingConfig = if (System.getenv("RELEASE_KEYSTORE_PATH") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
        buildConfig = false
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        // Plain-text report so CI can print problems like calls that need a newer Android.
        textReport = true
        textOutput = file("build/reports/lint-results.txt")
    }

    testOptions {
        // Unit tests run on the JVM; Android framework calls that aren't needed return defaults.
        unitTests.isReturnDefaultValues = true
        // The settings smoke test runs the real screen under Robolectric, which needs the app's resources.
        unitTests.isIncludeAndroidResources = true
        // The smoke test runs on its own (-PsmokeOnly) so the quick tests never wait for it or depend on it.
        unitTests.all {
            if (project.hasProperty("smokeOnly")) {
                it.filter.includeTestsMatching("*SettingsSmokeTest")
            } else {
                it.filter.excludeTestsMatching("*SettingsSmokeTest")
            }
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // The real org.json, because the one in the Android stub jar does nothing in unit tests.
    testImplementation("org.json:json:20231013")
    // Runs the Settings screen on the JVM for the smoke test.
    testImplementation("org.robolectric:robolectric:4.13")
}
