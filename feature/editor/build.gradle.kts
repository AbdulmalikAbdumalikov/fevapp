plugins {
    alias(libs.plugins.fevapp.android.feature)
}

android {
    namespace = "uz.selerown.fevapp.feature.editor"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":engine:timeline"))
    implementation(project(":engine:render"))
    implementation(project(":engine:effects"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.media3.ui)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
}
