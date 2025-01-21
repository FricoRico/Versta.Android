package app.versta.translate.core.entity

class TranslationMemoryCache(private val maxSize: Int = 64) {
    private val languagePairCaches = mutableMapOf<String, LinkedHashMap<Int, String>>()

    private fun getCacheKeyForLanguagePair(languages: LanguagePair): String {
        return "${languages.source.isoCode}-${languages.target.isoCode}"
    }

    private fun getCacheForLanguagePair(languages: LanguagePair): LinkedHashMap<Int, String> {
        val languagePairKey = getCacheKeyForLanguagePair(languages)
        return languagePairCaches.getOrPut(languagePairKey) {
            object : LinkedHashMap<Int, String>(maxSize, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean {
                    return size > maxSize
                }
            }
        }
    }

    fun get(key: String, languages: LanguagePair): String? {
        val cache = getCacheForLanguagePair(languages)
        return cache[key.hashCode()]
    }

    fun put(key: String, value: String, languages: LanguagePair) {
        val cache = getCacheForLanguagePair(languages)
        cache[key.hashCode()] = value
    }

    fun put(keys: List<String>, values: List<String>, languages: LanguagePair) {
        val cache = getCacheForLanguagePair(languages)
        keys.zip(values).forEach { (k, v) -> cache[k.hashCode()] = v }
    }

    fun clear(languages: LanguagePair? = null) {
        if (languages != null) {
            val languagePairKey = getCacheKeyForLanguagePair(languages)
            languagePairCaches.remove(languagePairKey)
            return
        }

        languagePairCaches.clear()
    }

    fun size(languages: LanguagePair? = null): Int {
        if (languages != null) {
            val languagePairKey = getCacheKeyForLanguagePair(languages)
            return languagePairCaches[languagePairKey]?.size ?: 0
        }

        return languagePairCaches.values.sumOf { it.size }
    }

    fun keys(languages: LanguagePair? = null): Set<Int> {
        if (languages != null) {
            val languagePairKey = getCacheKeyForLanguagePair(languages)
            return languagePairCaches[languagePairKey]?.keys ?: emptySet()
        }

        return languagePairCaches.values.flatMap { it.keys }.toSet()
    }
}
