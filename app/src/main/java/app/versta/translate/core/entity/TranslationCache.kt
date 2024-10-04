package app.versta.translate.core.entity

data class CachResult (
    val cached: List<String> = emptyList(),
    val missing: List<String> = emptyList()
)

class TranslationCache(private val maxSize: Int) {
    private val cache = object : LinkedHashMap<Int, String>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean {
            return size > maxSize
        }
    }

    fun get(key: String): String? {
        return cache[key.hashCode()]
    }

    fun get(key: List<String>): CachResult {
        val cached = key.mapNotNull { cache[it.hashCode()] }
        val missing = cached.filter { it !in cached }

        return CachResult(cached, missing)
    }

    fun put(key: String, value: String) {
        cache[key.hashCode()] = value
    }

    fun put(key: List<String>, value: List<String>) {
        key.zip(value).forEach { (k, v) -> cache[k.hashCode()] = v }
    }

    fun clear() {
        cache.clear()
    }

    fun size(): Int {
        return cache.size
    }

    fun keys(): Set<Int> {
        return cache.keys
    }
}
