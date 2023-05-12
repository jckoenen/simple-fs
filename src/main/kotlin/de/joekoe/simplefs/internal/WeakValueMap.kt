package de.joekoe.simplefs.internal

import java.lang.ref.WeakReference

internal class WeakValueMap<K, V> {
    private val underlying = mutableMapOf<K, WeakReference<V>>()

    operator fun set(key: K, value: V) {
        underlying[key] = WeakReference(value)
    }

    operator fun get(key: K): V? {
        val ref = underlying[key]
        val value = ref?.get()
        if (value == null && ref != null) underlying.remove(key)
        return value
    }

    fun remove(key: K) = underlying.remove(key)?.get()
}

internal fun <K, V> WeakValueMap<K, V>.reKey(oldKey: K, newKey: K) {
    remove(oldKey)?.let { set(newKey, it) }
}

internal fun <K, V> MutableMap<K, V>.reKey(oldKey: K, newKey: K) {
    remove(oldKey)?.let { set(newKey, it) }
}
