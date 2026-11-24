package uz.selerown.fevapp.engine.effects

/**
 * Central list of available plugins. Feature modules register their plugins
 * at startup; tool panels read [filters], [effects] and [transitions].
 */
class PluginRegistry {
    private val plugins = linkedMapOf<String, EditorPlugin>()

    fun register(plugin: EditorPlugin) {
        require(plugin.id !in plugins) { "Duplicate plugin id: ${plugin.id}" }
        plugins[plugin.id] = plugin
    }

    fun find(id: String): EditorPlugin? = plugins[id]

    val filters: List<Filter> get() = plugins.values.filterIsInstance<Filter>()
    val effects: List<Effect> get() = plugins.values.filterIsInstance<Effect>()
    val transitions: List<Transition> get() = plugins.values.filterIsInstance<Transition>()
}
