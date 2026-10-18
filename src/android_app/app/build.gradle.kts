import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.smartparking"
    compileSdk = 36

    defaultConfig {
        manifestPlaceholders += mapOf()
        applicationId = "com.smartparking"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Lee las API keys del local.properties
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())

        buildConfigField("String", "SUPABASE_API_KEY", "\"${properties.getProperty("SUPABASE_API_KEY")}\"")
        buildConfigField("String", "MAPS_API_KEY", "\"${properties.getProperty("MAPS_API_KEY")}\"")
        buildConfigField("String", "STRIPE_PUBLISHABLE_KEY", "\"${properties.getProperty("STRIPE_PUBLISHABLE_KEY")}\"")

        // Para Maps también se necesita en el Manifest
        manifestPlaceholders["MAPS_API_KEY"] = properties.getProperty("MAPS_API_KEY")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Esto es para hacer el enlace entre los layouts
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    implementation("com.google.code.gson:gson:2.10")

    // Para el mapa
    implementation("com.google.android.gms:play-services-maps:19.2.0") //Revisar, La versión en el tutorial es la 18.0.1, aquí se me sugiere que sea por lo menos la 18.2.0 y la más nueva es la 19.2.0; aunque también me sugiere que mejor remplace todito dentro del  paréntesis por libs.play.services.maps
    implementation("com.google.android.material:material:1.12.0")

    // Google Play Services para ubicación
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Glide para cargar imágenes
    implementation ("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.16.0")

    // Para Stripe
    implementation("com.stripe:stripe-android:20.1.0")

    // ZXing para escaneo de QR
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}