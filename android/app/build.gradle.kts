import java.util.Properties

plugins {
    // AGP 9 compiles Kotlin itself; org.jetbrains.kotlin.android must not be
    // applied alongside it.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Signing material is kept out of the repository. Without it, release builds
// still assemble but come out unsigned. Read through `providers` so the file's
// *contents* are a tracked configuration-cache input, not just its existence.
val keystoreProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
        .asText.orNull?.let { load(it.reader()) }
}

android {
    namespace = "dev.jebaum.isometric"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.jebaum.isometric"
        // The Galaxy S25 runs Android 16 (API 36). minSdk cannot exceed the
        // device's API level or the APK will not install.
        minSdk = 36
        // Held at 36 rather than 37 on purpose: targetSdk behaviour changes are
        // gated inside the platform, so 37 is inert on Android 16 anyway, and
        // would silently switch on untested behaviour the day the phone updates.
        // Bump deliberately, once there is an Android 17 device to test on.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Distinct id so a debug build can sit alongside the signed release
            // instead of failing to install over it on a signature mismatch.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Anything new must be dealt with rather than joining a pile of accepted
        // noise. The four below are documented decisions; see README.md/ICONS.md.
        warningsAsErrors = true
        disable += setOf(
            // targetSdk held at 36 on purpose.
            "OldTargetApi",
            // Dropping the -v26 qualifier makes aapt2 fail to resolve the icon.
            "ObsoleteSdkInt",
            // The wireframe artwork cannot survive being tinted flat.
            "MonochromeLauncherIcon",
        )
        // Kotlin has to track the KGP that AGP ships rather than the newest
        // release, so these cannot be allowed to fail the build — but they are
        // still worth seeing for the AndroidX dependencies, so they are demoted
        // rather than switched off. Both ids report the same pin.
        informational += setOf("GradleDependency", "NewerVersionAvailable")
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit)
}
