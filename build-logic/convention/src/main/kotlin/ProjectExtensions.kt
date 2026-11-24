import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** Access to gradle/libs.versions.toml from convention plugins. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias).get().requiredVersion

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

/** Java/Kotlin bytecode target used by every module. */
internal val FEVAPP_JAVA_VERSION = JavaVersion.VERSION_17

/** Shared Android settings for application and library modules. */
internal fun Project.configureAndroidCommon() {
    val compile = libs.version("compileSdk").toInt()
    val compileMinor = libs.version("compileSdkMinor").toInt()
    val min = libs.version("minSdk").toInt()
    extensions.findByType(ApplicationExtension::class.java)?.apply {
        compileSdk = compile
        compileSdkMinor = compileMinor
        defaultConfig.minSdk = min
        compileOptions.sourceCompatibility = FEVAPP_JAVA_VERSION
        compileOptions.targetCompatibility = FEVAPP_JAVA_VERSION
    }
    extensions.findByType(LibraryExtension::class.java)?.apply {
        compileSdk = compile
        compileSdkMinor = compileMinor
        defaultConfig.minSdk = min
        compileOptions.sourceCompatibility = FEVAPP_JAVA_VERSION
        compileOptions.targetCompatibility = FEVAPP_JAVA_VERSION
    }
}
