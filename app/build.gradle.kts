plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.fl0w.speye"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fl0w.speye"
        minSdk = 24
        targetSdk = 37
        versionCode = 1108261007
        versionName = "1.6.0-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("googlePlay") {
            dimension = "distribution"
            applicationIdSuffix = ".play"
            versionNameSuffix = "-play"
            proguardFiles("src/googlePlay/proguard-rules-googleplay.pro")
        }
        create("foss") {
            dimension = "distribution"
            versionNameSuffix = "-foss"
        }
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
        getByName("test").assets.directories.add("$projectDir/schemas")
    }
}

tasks.matching { it.name.contains("Foss", ignoreCase = true) && it.name.contains("GoogleServices", ignoreCase = true) }.configureEach {
    enabled = false
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)

    // Compose
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Flavor specific dependencies
    "googlePlayImplementation"(libs.play.services.ads)
    "googlePlayImplementation"("androidx.javascriptengine:javascriptengine:1.0.0-beta01")
    "googlePlayImplementation"(libs.play.billing.ktx)
    "googlePlayImplementation"(libs.speye.credentials)
    "googlePlayImplementation"(libs.speye.credentials.play)
    "googlePlayImplementation"(libs.speye.googleid)
    "googlePlayImplementation"(libs.speye.drive.api)
    "googlePlayImplementation"(libs.speye.drive.client)
    "googlePlayImplementation"(libs.speye.http.gson)
    "googlePlayImplementation"("com.google.http-client:google-http-client-android:1.44.1")
    "googlePlayImplementation"(libs.speye.work.ktx)
    "googlePlayImplementation"(libs.speye.play.auth)

    // Firebase (Google Play Only)
    val firebaseBom = platform(libs.firebase.bom)
    "googlePlayImplementation"(firebaseBom)
    "googlePlayImplementation"(libs.firebase.auth.ktx)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
}
