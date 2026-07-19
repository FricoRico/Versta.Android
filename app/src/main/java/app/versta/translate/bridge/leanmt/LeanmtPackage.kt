package app.versta.translate.bridge.leanmt

/**
 * Model bundle descriptor forwarded to leanmt's `leanmt::Package`. Paths
 * point at the extracted model artifacts. `targetVocabulary` is left
 * empty for shared-vocabulary models; two-vocabulary models (e.g. en-zh)
 * require it.
 */
data class LeanmtPackage(
    val model: String,
    val vocabulary: String,
    val targetVocabulary: String,
    val shortlist: String,
)
