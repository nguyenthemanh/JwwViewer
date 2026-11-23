package com.jwwviewer.app.ui.adaptive

import android.app.Activity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

@Immutable
data class FoldInfo(
    val isSeparating: Boolean,
    val isBookPosture: Boolean,
    val isTabletopPosture: Boolean,
    val orientation: FoldingFeature.Orientation?,
)

@Immutable
data class AdaptiveLayout(
    val widthClass: WindowWidthSizeClass,
    val heightClass: WindowHeightSizeClass,
    /** List + detail side-by-side (Fold inner, tablet, desktop) */
    val useDualPane: Boolean,
    /** Tabletop: controls above / canvas below hinge */
    val useTabletopSplit: Boolean,
    val paneListWeight: Float,
    val paneDetailWeight: Float,
    val contentMaxWidth: Dp?,
    val horizontalPadding: Dp,
    val fold: FoldInfo?,
) {
    val isCompact: Boolean get() = widthClass == WindowWidthSizeClass.Compact
    val isExpanded: Boolean get() = widthClass == WindowWidthSizeClass.Expanded
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberAdaptiveLayout(): AdaptiveLayout {
    val activity = LocalContext.current as Activity
    val sizeClass = calculateWindowSizeClass(activity)
    val fold = rememberFoldInfo(activity)
    return remember(sizeClass, fold) { sizeClass.toAdaptive(fold) }
}

@Composable
private fun rememberFoldInfo(activity: Activity): FoldInfo? {
    val tracker = remember(activity) { WindowInfoTracker.getOrCreate(activity) }
    val layoutInfo by tracker.windowLayoutInfo(activity)
        .collectAsStateWithLifecycle(initialValue = WindowLayoutInfo(emptyList()))

    return remember(layoutInfo) {
        val feature = layoutInfo.displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull()
            ?: return@remember null
        FoldInfo(
            isSeparating = feature.isSeparating,
            isBookPosture = feature.state == FoldingFeature.State.HALF_OPENED &&
                feature.orientation == FoldingFeature.Orientation.VERTICAL,
            isTabletopPosture = feature.state == FoldingFeature.State.HALF_OPENED &&
                feature.orientation == FoldingFeature.Orientation.HORIZONTAL,
            orientation = feature.orientation,
        )
    }
}

private fun WindowSizeClass.toAdaptive(fold: FoldInfo?): AdaptiveLayout {
    val dual = when {
        fold?.isBookPosture == true -> true
        fold?.isSeparating == true &&
            widthSizeClass != WindowWidthSizeClass.Compact -> true
        widthSizeClass == WindowWidthSizeClass.Expanded -> true
        widthSizeClass == WindowWidthSizeClass.Medium &&
            heightSizeClass != WindowHeightSizeClass.Compact -> true
        else -> false
    }

    val maxWidth: Dp? = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> null
        WindowWidthSizeClass.Medium -> 840.dp
        else -> 1120.dp
    }
    val pad = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 20.dp
        WindowWidthSizeClass.Medium -> 28.dp
        else -> 36.dp
    }

    val (listW, detailW) = when {
        fold?.isBookPosture == true -> 0.48f to 0.52f
        widthSizeClass == WindowWidthSizeClass.Expanded -> 0.32f to 0.68f
        else -> 0.38f to 0.62f
    }

    return AdaptiveLayout(
        widthClass = widthSizeClass,
        heightClass = heightSizeClass,
        useDualPane = dual,
        useTabletopSplit = fold?.isTabletopPosture == true,
        paneListWeight = listW,
        paneDetailWeight = detailW,
        contentMaxWidth = maxWidth,
        horizontalPadding = pad,
        fold = fold,
    )
}
