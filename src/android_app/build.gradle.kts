// build.gradle.kts creado para declarar los plugins necesarios en la raíz
// src/android_app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}