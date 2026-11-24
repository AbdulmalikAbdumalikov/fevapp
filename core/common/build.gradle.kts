plugins {
    alias(libs.plugins.fevapp.jvm.library)
}

dependencies {
    api(libs.javax.inject)
    api(libs.kotlinx.coroutines.core)
}
