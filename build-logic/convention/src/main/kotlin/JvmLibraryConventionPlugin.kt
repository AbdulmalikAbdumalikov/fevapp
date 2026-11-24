import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Pure Kotlin/JVM module (no Android). Used for the domain model and the
 * timeline engine so their logic is fast to unit test on the desktop JVM.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            // Kotlin picks up the same toolchain, so bytecode targets stay in sync.
            toolchain.languageVersion.set(JavaLanguageVersion.of(FEVAPP_JAVA_VERSION.majorVersion))
        }
        dependencies {
            add("testImplementation", libs.lib("junit"))
            add("testImplementation", libs.lib("kotlin-test"))
        }
    }
}
