import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Applies the Android application plugin with Fevapp defaults. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        configureAndroidCommon()
        extensions.configure<ApplicationExtension> {
            defaultConfig.targetSdk = libs.version("targetSdk").toInt()
        }
    }
}
