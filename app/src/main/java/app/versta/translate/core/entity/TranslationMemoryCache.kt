package app.versta.translate.core.entity

data class CacheResult (
    val cached: List<String> = emptyList(),
    val missing: List<String> = emptyList()
)

class TranslationMemoryCache(private val maxSize: Int = 64) {
    private val cache = object : LinkedHashMap<Int, String>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean {
            return size > maxSize
        }
    }

    fun get(key: String): String? {
        return cache[key.hashCode()]
    }

    fun get(key: List<String>): CacheResult {
        val cached = key.mapNotNull { cache[it.hashCode()] }
        val missing = key.filter { it !in cached }

        return CacheResult(cached, missing)
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
