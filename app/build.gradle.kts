plugins {
    id("com.android.application")
}

android {
    namespace = "com.kaifshaik.musicplayer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kaifshaik.musicplayer"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }
}

dependencies {

    // Lottie Animation
    implementation ("com.airbnb.android:lottie:6.0.0")

    // Pallete
    implementation ("androidx.palette:palette:1.0.0")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // media
    implementation ("androidx.media:media:1.7.0")

    // Player
    implementation ("org.videolan.android:libvlc-all:3.4.0")

    // ViewPager2
    implementation ("androidx.viewpager2:viewpager2:1.1.0")

    // Making Responsive
    implementation ("com.intuit.sdp:sdp-android:1.1.1")
    implementation ("com.intuit.ssp:ssp-android:1.1.1")

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}