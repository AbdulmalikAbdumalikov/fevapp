plugins {
    `kotlin-dsl`
}

group = "uz.selerown.fevapp.buildlogic"

dependencies {
    // Only AGP types are referenced from Kotlin code; other plugins are applied by id.
    compileOnly(libs.android.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "fevapp.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "fevapp.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "fevapp.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "fevapp.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("hilt") {
            id = "fevapp.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "fevapp.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
