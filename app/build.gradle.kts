plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.kas_app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.kas_app"
        minSdk = 24
        targetSdk = 35
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
    testOptions {
        unitTests {
            // Lets the JVM unit tests exercise real SQLite through Robolectric.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.core.splashscreen)
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    // Explicit: the main screen root is a CoordinatorLayout (also present
    // transitively via Material, but the layout inflates it directly).
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}