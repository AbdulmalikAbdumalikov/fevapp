plugins {
    alias(libs.plugins.fevapp.android.application)
    alias(libs.plugins.fevapp.android.compose)
    alias(libs.plugins.fevapp.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "uz.selerown.fevapp"

    defaultConfig {
        applicationId = "uz.selerown.fevapp"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))
    implementation(project(":feature:home"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:export"))
    implementation(project(":engine:render")) // TextRenderer.installFonts

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
}
