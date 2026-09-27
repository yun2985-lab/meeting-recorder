plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.vlab.meetingrecorder"; compileSdk = 35
    defaultConfig { applicationId = "com.vlab.meetingrecorder"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation("androidx.core:core:1.15.0"); implementation(files("libs/sherpa-onnx.aar")) }
android { packaging { jniLibs { useLegacyPackaging = true } } }
