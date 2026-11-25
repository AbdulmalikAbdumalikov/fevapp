plugins {
    alias(libs.plugins.fevapp.android.feature)
}

android {
    namespace = "uz.selerown.fevapp.feature.export"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":engine:render"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
