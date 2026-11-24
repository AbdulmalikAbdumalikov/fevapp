plugins {
    alias(libs.plugins.fevapp.android.library)
    alias(libs.plugins.fevapp.hilt)
}

android {
    namespace = "uz.selerown.fevapp.engine.render"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":engine:effects"))

    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.common)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.kotlinx.coroutines.android)
}
