plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "de.myapphub"
 compileSdk = 35
 defaultConfig { applicationId = "de.myapphub"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
 buildTypes { release { isMinifyEnabled = false } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("androidx.core:core-ktx:1.15.0") }
