package app.versta.translate.utils

import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass

fun WindowAdaptiveInfo.isWide() = this.windowSizeClass.isWidthAtLeastBreakpoint(widthDpBreakpoint = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
fun WindowAdaptiveInfo.isExpanded() = this.windowSizeClass.isAtLeastBreakpoint(
    widthDpBreakpoint = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    heightDpBreakpoint = WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
)