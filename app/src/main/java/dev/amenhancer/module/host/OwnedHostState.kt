package dev.amenhancer.module.host

/** Tracks a group of view properties across a synchronous module update on its owning thread. */
class OwnedHostState<K, V>(initial: Map<K, V>) {
    private val native = initial.toMutableMap()
    private val owned = initial.toMutableMap()
    fun observeNative(current: Map<K, V>) {
        current.forEach { (key, value) -> if (value != owned[key]) native[key] = value }
    }
    fun captureOwned(current: Map<K, V>) {
        current.forEach { (key,value) -> owned[key]=value }
        owned.keys.retainAll(current.keys)
    }
    fun restoreValues(current: Map<K, V>): Map<K, V> = current.mapValues { (key,value) ->
        if (value == owned[key] && native.containsKey(key)) native.getValue(key) else value
    }
    fun nativeValue(key: K): V = native.getValue(key)
}
