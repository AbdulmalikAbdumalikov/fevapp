package uz.selerown.fevapp.engine.effects

/**
 * Extension points for future features (filters, effects, transitions).
 *
 * Plugins are pure descriptions: the render engine turns them into Media3
 * effects (OpenGL ES shaders) at preview/export time. Keeping them free of
 * Android types lets the timeline store and undo them like any other data.
 */
interface EditorPlugin {
    /** Stable id stored in projects, e.g. "filter.warm". Never rename. */
    val id: String

    /** Key of the user-visible name in strings.xml (resolved by the UI layer). */
    val nameKey: String

    val parameters: List<PluginParameter>
}

/** A user-adjustable value with a slider in the UI. */
data class PluginParameter(
    val key: String,
    val nameKey: String,
    val min: Float,
    val max: Float,
    val default: Float,
)

/** Applied to a single clip's frames (e.g. blur, glitch). */
interface Effect : EditorPlugin

/** Color look applied to a clip (e.g. warm, mono) or adjustment (brightness). */
interface Filter : EditorPlugin

/** Blends the end of one clip into the start of the next. */
interface Transition : EditorPlugin {
    val defaultDurationUs: Long
}

/** Saved instance of a plugin with concrete parameter values. */
data class PluginInstance(
    val pluginId: String,
    val values: Map<String, Float>,
)
