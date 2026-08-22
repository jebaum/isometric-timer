buildscript {
    dependencies {
        // AGP 9's built-in Kotlin ships KGP 2.2.10; this is AGP's documented
        // way to run a newer stable KGP. Keep it equal to `kotlin` in
        // gradle/libs.versions.toml so one version wins everywhere.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
