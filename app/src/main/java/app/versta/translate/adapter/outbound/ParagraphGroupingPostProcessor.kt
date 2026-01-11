package app.versta.translate.adapter.outbound

import android.graphics.PointF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.versta.translate.core.entity.ObjectCharacterRecogniserColors
import app.versta.translate.core.entity.ObjectCharacterRecogniserResult
import timber.log.Timber
import kotlin.math.sqrt

/**
 * Post-processor that groups OCR text lines into paragraphs based on spatial proximity,
 * estimated line height, and color similarity.
 *
 * @param verticalThresholdFactor Multiplier for line height to determine vertical grouping threshold
 * @param horizontalEps Maximum horizontal distance (in pixels) for grouping lines into same paragraph
 * @param colorTolerance Maximum color distance (0-1) to consider colors similar
 */
class ParagraphGroupingPostProcessor(
    private val verticalThresholdFactor: Float = 1.5f,
    private val horizontalEps: Float = 30f,
    private val colorTolerance: Float = 0.25f,
    private val fontSizeTolerance: Float = 0.10f
) : OcrPostProcessor {

    override fun process(context: OcrPostProcessorContext): OcrPostProcessorContext {
        val results = context.results

        if (results.isEmpty()) return context
        if (results.size == 1) {
            return context.copy(
                results = results.map { result ->
                    ObjectCharacterRecogniserResult(
                        points = result.points,
                        score = result.score,
                        tokens = result.tokens,
                        text = result.text,
                        colors = result.colors,
                        lines = result.lines,
                        fontSize = result.fontSize,
                        fontWeight = result.fontWeight
                    )
                }
            )
        }

        return try {
            context.copy(results = groupIntoParagraphs(results))
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error grouping paragraphs, returning original results")
            context
        }
    }

    private fun groupIntoParagraphs(results: List<ObjectCharacterRecogniserResult>): List<ObjectCharacterRecogniserResult> {
        // Step 1: Sort by Y-coordinate (top to bottom)
        val sortedResults = results.sortedBy { result ->
            // Use top-left Y coordinate for sorting
            result.points.minOfOrNull { it.y } ?: 0f
        }

        // Step 2: Group by vertical proximity (agglomerative-style clustering)
        val verticalGroups = groupByVerticalProximity(sortedResults)

        // Step 3: Within each vertical group, group by horizontal overlap
        val paragraphs = mutableListOf<List<ObjectCharacterRecogniserResult>>()
        for (verticalGroup in verticalGroups) {
            val horizontalGroups = groupByHorizontalProximity(verticalGroup)
            paragraphs.addAll(horizontalGroups)
        }

        // Step 4: Merge each paragraph group into a single result
        val merged = paragraphs.map { group -> mergeParagraph(group) }


        return merged
    }

    private fun groupByVerticalProximity(results: List<ObjectCharacterRecogniserResult>): List<List<ObjectCharacterRecogniserResult>> {
        if (results.isEmpty()) return emptyList()

        val groups = mutableListOf<MutableList<ObjectCharacterRecogniserResult>>()
        var currentGroup = mutableListOf(results.first())

        for (i in 1 until results.size) {
            val current = results[i]
            val previous = results[i - 1]

            val prevBottomY = previous.points.maxOfOrNull { it.y } ?: 0f
            val currentTopY = current.points.minOfOrNull { it.y } ?: 0f
            val verticalGap = currentTopY - prevBottomY

            val prevLineHeight = estimateLineHeight(previous)
            val currentLineHeight = estimateLineHeight(current)
            val avgLineHeight = (prevLineHeight + currentLineHeight) / 2f

            val threshold = avgLineHeight * verticalThresholdFactor

            // Check color similarity
            val colorsAreSimilar = areStylesSimilar(previous, current)

            if (verticalGap <= threshold && colorsAreSimilar) {
                currentGroup.add(current)
            } else {
                groups.add(currentGroup)
                currentGroup = mutableListOf(current)
            }
        }
        groups.add(currentGroup)

        return groups
    }

    private fun groupByHorizontalProximity(results: List<ObjectCharacterRecogniserResult>): List<List<ObjectCharacterRecogniserResult>> {
        if (results.isEmpty()) return emptyList()
        if (results.size == 1) return listOf(results)

        // Sort by X-coordinate (left to right)
        val sortedByX = results.sortedBy { result ->
            result.points.minOfOrNull { it.x } ?: 0f
        }

        // Use DBSCAN-style clustering
        val visited = BooleanArray(sortedByX.size)
        val clusters = mutableListOf<MutableList<ObjectCharacterRecogniserResult>>()

        for (i in sortedByX.indices) {
            if (visited[i]) continue

            val cluster = mutableListOf<ObjectCharacterRecogniserResult>()
            val queue = mutableListOf(i)
            visited[i] = true

            while (queue.isNotEmpty()) {
                val currentIdx = queue.removeAt(0)
                val current = sortedByX[currentIdx]
                cluster.add(current)

                // Find neighbors
                for (j in sortedByX.indices) {
                    if (visited[j]) continue

                    val neighbor = sortedByX[j]
                    if (areHorizontallyClose(current, neighbor, horizontalEps) &&
                        areStylesSimilar(current, neighbor)) {
                        visited[j] = true
                        queue.add(j)
                    }
                }
            }

            if (cluster.isNotEmpty()) {
                clusters.add(cluster)
            }
        }

        return clusters
    }

    private fun areHorizontallyClose(a: ObjectCharacterRecogniserResult, b: ObjectCharacterRecogniserResult, eps: Float): Boolean {
        val aMinX = a.points.minOfOrNull { it.x } ?: 0f
        val aMaxX = a.points.maxOfOrNull { it.x } ?: 0f
        val bMinX = b.points.minOfOrNull { it.x } ?: 0f
        val bMaxX = b.points.maxOfOrNull { it.x } ?: 0f

        // Check for overlap or proximity
        val horizontalGap = if (aMaxX < bMinX) {
            bMinX - aMaxX
        } else if (bMaxX < aMinX) {
            aMinX - bMaxX
        } else {
            0f // They overlap
        }

        return horizontalGap <= eps
    }

    private fun mergeParagraph(group: List<ObjectCharacterRecogniserResult>): ObjectCharacterRecogniserResult {
        if (group.isEmpty()) {
            throw IllegalArgumentException("Cannot merge empty group")
        }
        if (group.size == 1) {
            val single = group.first()
            return ObjectCharacterRecogniserResult(
                points = single.points,
                score = single.score,
                tokens = single.tokens,
                text = single.text,
                colors = single.colors,
                lines = listOf(single.text),
                fontSize = single.fontSize,
                fontWeight = single.fontWeight
            )
        }

        // Sort group by reading order: top-to-bottom, then left-to-right
        // This ensures text flows naturally regardless of clustering order
        val sortedGroup = group.sortedWith(compareBy(
            { result -> result.points.minOfOrNull { it.y } ?: 0f },  // Primary: Y position (top to bottom)
            { result -> result.points.minOfOrNull { it.x } ?: 0f }   // Secondary: X position (left to right)
        ))


        // Compute combined bounding polygon (min/max corners)
        val allPoints = sortedGroup.flatMap { it.points.toList() }
        val minX = allPoints.minOfOrNull { it.x } ?: 0f
        val minY = allPoints.minOfOrNull { it.y } ?: 0f
        val maxX = allPoints.maxOfOrNull { it.x } ?: 0f
        val maxY = allPoints.maxOfOrNull { it.y } ?: 0f

        // Create a combined bounding box (4 corners: top-left, top-right, bottom-right, bottom-left)
        val combinedPoints = arrayOf(
            PointF(minX, minY),
            PointF(maxX, minY),
            PointF(maxX, maxY),
            PointF(minX, maxY)
        )

        // Concatenate text with newlines, preserving reading order
        val lines = sortedGroup.map { it.text }
        val combinedText = lines.joinToString("\n")


        // Average the scores
        val avgScore = sortedGroup.map { it.score }.average().toFloat()

        // Use the first result's colors (they should be similar due to grouping)
        val colors = sortedGroup.first().colors

        // Combine tokens (concatenate all tokens in reading order)
        val combinedTokens = sortedGroup.flatMap { it.tokens.toList() }.toLongArray()

        // Average font size from all lines
        val avgFontSize = sortedGroup.map { it.fontSize }.average().toFloat()

        // Use the first result's font weight (they should be the same due to grouping)
        val fontWeight = sortedGroup.first().fontWeight

        return ObjectCharacterRecogniserResult(
            points = combinedPoints,
            score = avgScore,
            tokens = combinedTokens,
            text = combinedText,
            colors = colors,
            lines = lines,
            fontSize = avgFontSize,
            fontWeight = fontWeight
        )
    }

    private fun estimateLineHeight(result: ObjectCharacterRecogniserResult): Float {
        val minY = result.points.minOfOrNull { it.y } ?: 0f
        val maxY = result.points.maxOfOrNull { it.y } ?: 0f
        return maxY - minY
    }

    private fun areColorsSimilar(a: ObjectCharacterRecogniserColors, b: ObjectCharacterRecogniserColors): Boolean {
        val bgDistance = colorDistance(a.background, b.background)
        val fgDistance = colorDistance(a.foreground, b.foreground)

        // Both background and foreground should be similar
        return bgDistance <= colorTolerance && fgDistance <= colorTolerance
    }

    private fun areFontSizesSimilar(a: Float, b: Float): Boolean {
        if (a == 0f || b == 0f) return true // Uninitialized font sizes always match

        val maxSize = kotlin.math.max(a, b)
        val minSize = kotlin.math.min(a, b)
        val sizeDifference = (maxSize - minSize) / maxSize

        return sizeDifference <= fontSizeTolerance
    }

    private fun areStylesSimilar(a: ObjectCharacterRecogniserResult, b: ObjectCharacterRecogniserResult): Boolean {
        // Check color similarity
        if (!areColorsSimilar(a.colors, b.colors)) {
            return false
        }

        // Check font weight matching - don't group bold with regular
        if (a.fontWeight != b.fontWeight) {
            return false
        }

        // Check font size similarity
        if (!areFontSizesSimilar(a.fontSize, b.fontSize)) {
            return false
        }

        return true
    }

    private fun colorDistance(a: Color, b: Color): Float {
        val aArgb = a.toArgb()
        val bArgb = b.toArgb()

        val aR = (aArgb shr 16 and 0xFF) / 255f
        val aG = (aArgb shr 8 and 0xFF) / 255f
        val aB = (aArgb and 0xFF) / 255f

        val bR = (bArgb shr 16 and 0xFF) / 255f
        val bG = (bArgb shr 8 and 0xFF) / 255f
        val bB = (bArgb and 0xFF) / 255f

        // Euclidean distance in RGB space (normalized)
        val distance = sqrt(
            (aR - bR) * (aR - bR) +
            (aG - bG) * (aG - bG) +
            (aB - bB) * (aB - bB)
        ) / sqrt(3f) // Normalize to [0, 1]

        return distance
    }

    companion object {
        private val TAG: String = ParagraphGroupingPostProcessor::class.java.simpleName
    }
}

