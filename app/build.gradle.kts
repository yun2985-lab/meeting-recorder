plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.vlab.meetingrecorder"; compileSdk = 35
    defaultConfig { applicationId = "com.vlab.meetingrecorder"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
}
dependencies { implementation("androidx.core:core:1.15.0") }
