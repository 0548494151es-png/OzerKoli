plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// מספר הגרסה מגיע מ-GitHub Actions (-Pvcode=מספר ההרצה), כדי שכל גרסה חדשה תהיה גבוהה מהקודמת
val vcode = (project.findProperty("vcode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "il.ozer.koli"
    compileSdk = 34

    defaultConfig {
        applicationId = "il.ozer.koli"
        minSdk = 26
        targetSdk = 34
        versionCode = vcode
        versionName = "1.$vcode"
    }

    // חתימה קבועה: חובה כדי שהאפליקציה תוכל להתעדכן על עצמה
    signingConfigs {
        create("ozer") {
            storeFile = file("ozer.jks")
            storePassword = "ozerkoli"
            keyAlias = "ozer"
            keyPassword = "ozerkoli"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("ozer")
        }
        debug {
            signingConfig = signingConfigs.getByName("ozer")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
