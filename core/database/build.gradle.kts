plugins {
    alias(libs.plugins.fevapp.android.library)
    alias(libs.plugins.fevapp.hilt)
}

android {
    namespace = "uz.selerown.fevapp.core.database"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
}
