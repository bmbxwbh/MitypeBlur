plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mitype.blur"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mitype.blur"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.8.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    flavorDimensions += "api"
    productFlavors {
        create("api101") {
            dimension = "api"
            applicationIdSuffix = ".api101"
            versionNameSuffix = "-api101"
        }
        create("api102") {
            dimension = "api"
            applicationIdSuffix = ".api102"
            versionNameSuffix = "-api102"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)

    "api101CompileOnly"(libs.libxposed.api101)
    "api101Implementation"(libs.libxposed.service101)
    "api102CompileOnly"(libs.libxposed.api102)
    "api102Implementation"(libs.libxposed.service102)

    debugImplementation(libs.compose.ui.tooling)
}
