package com.godseyeview.layerapi

/** The core's registry of installed layers. Layers register at app start;
 *  the registry is the single source of truth for the layer panel. */
class LayerRegistry {
    private val layers = LinkedHashMap<String, Layer>()

    @Synchronized
    fun register(layer: Layer) {
        require(!layers.containsKey(layer.id)) { "duplicate layer id: ${layer.id}" }
        layers[layer.id] = layer
    }

    @Synchronized
    fun unregister(id: String) { layers.remove(id) }

    @Synchronized
    fun get(id: String): Layer? = layers[id]

    @Synchronized
    fun all(): List<Layer> = layers.values.toList()

    @Synchronized
    fun isEmpty(): Boolean = layers.isEmpty()
}
