import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

val adsPropsFile = rootProject.file("ads.properties")
val adsProps = Properties().apply {
    if (adsPropsFile.exists()) adsPropsFile.inputStream().use { load(it) }
}

fun adProp(key: String, fallback: String = ""): String =
    adsProps.getProperty(key, fallback).replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.universalconverter.pro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.universalconverter.pro"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
        manifestPlaceholders["admobAppId"] = adProp("admobAppId", "ca-app-pub-3940256099942544~3347511713")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"${adProp("bannerUnitId", "ca-app-pub-3940256099942544/9214589741")}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${adProp("interstitialUnitId", "ca-app-pub-3940256099942544/1033173712")}\"")
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"${adProp("rewardedUnitId", "ca-app-pub-3940256099942544/5224354917")}\"")
        buildConfigField("String", "BILLING_PUBLIC_KEY", "\"${adProp("billingPublicKey")}\"")
        buildConfigField("String", "REMOVE_ADS_PRODUCT_ID", "\"${adProp("removeAdsProductId", "remove_ads")}\"")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }

    bundle {
        language { enableSplit = false }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.01.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.12")
    implementation("org.jsoup:jsoup:1.23.2")

    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
