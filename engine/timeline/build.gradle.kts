plugins {
    alias(libs.plugins.fevapp.jvm.library)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
}
