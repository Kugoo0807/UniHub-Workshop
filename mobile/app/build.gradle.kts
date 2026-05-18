plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.mobile"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.mobile"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "environment"

    productFlavors {
        create("manualDev") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
        }

        create("dockerLocal") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2/\"")
        }

        create("realDevice") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"http://192.168.1.45/\"")
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.zxing.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.glide)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}