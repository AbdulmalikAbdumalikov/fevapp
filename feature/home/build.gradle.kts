plugins {
    alias(libs.plugins.fevapp.android.feature)
}

android {
    namespace = "uz.selerown.fevapp.feature.home"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":engine:render"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
}
