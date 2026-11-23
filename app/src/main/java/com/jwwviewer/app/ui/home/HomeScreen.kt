package com.jwwviewer.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DevicesFold
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jwwviewer.app.R
import com.jwwviewer.app.data.RecentFile
import com.jwwviewer.app.ui.adaptive.AdaptiveLayout
import com.jwwviewer.app.ui.adaptive.FoldInfo

@Composable
fun HomeScreen(
    recent: List<RecentFile>,
    adaptive: AdaptiveLayout,
    onOpenFile: () -> Unit,
    onOpenRecent: (RecentFile) -> Unit,
    onSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.primaryContainer.copy(alpha = 0.55f),
                        scheme.background,
                        scheme.background,
                    ),
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        val contentMod = Modifier
            .fillMaxSize()
            .then(
                if (adaptive.contentMaxWidth != null) {
                    Modifier.widthIn(max = adaptive.contentMaxWidth)
                } else {
                    Modifier
                },
            )
            .statusBarsPadding()
            .padding(horizontal = adaptive.horizontalPadding)

        if (adaptive.useDualPane && !adaptive.useTabletopSplit) {
            Row(modifier = contentMod) {
                HomePane(
                    recent = recent,
                    adaptive = adaptive,
                    useGrid = false,
                    onOpenFile = onOpenFile,
                    onOpenRecent = onOpenRecent,
                    onSettings = onSettings,
                    modifier = Modifier
                        .weight(adaptive.paneListWeight)
                        .fillMaxHeight(),
                )
                VerticalDivider()
                WideHintPane(
                    adaptive = adaptive,
                    modifier = Modifier
                        .weight(adaptive.paneDetailWeight)
                        .fillMaxHeight()
                        .padding(24.dp),
                )
            }
        } else {
            HomePane(
                recent = recent,
                adaptive = adaptive,
                useGrid = adaptive.widthClass != WindowWidthSizeClass.Compact,
                onOpenFile = onOpenFile,
                onOpenRecent = onOpenRecent,
                onSettings = onSettings,
                modifier = contentMod,
            )
        }
    }
}

@Composable
private fun HomePane(
    recent: List<RecentFile>,
    adaptive: AdaptiveLayout,
    useGrid: Boolean,
    onOpenFile: () -> Unit,
    onOpenRecent: (RecentFile) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Architecture,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(if (adaptive.isCompact) 36.dp else 44.dp),
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = if (adaptive.isCompact) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.displayLarge
                },
                color = scheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = scheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(if (adaptive.isCompact) 20.dp else 28.dp))
        Button(
            onClick = onOpenFile,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
        ) {
            Icon(Icons.Rounded.FileOpen, contentDescription = null)
            Spacer(modifier = Modifier.size(10.dp))
            Text(stringResource(R.string.open_file), style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.recent_files),
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))

        AnimatedVisibility(visible = recent.isEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(24.dp),
            ) {
                Column {
                    Icon(
                        Icons.Rounded.Description,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.empty_recent_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.empty_recent),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (useGrid) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(recent, key = { it.uri }) { item ->
                    RecentRow(item = item, onClick = { onOpenRecent(item) })
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(recent, key = { it.uri }) { item ->
                    RecentRow(item = item, onClick = { onOpenRecent(item) })
                }
            }
        }
    }
}

@Composable
private fun WideHintPane(
    adaptive: AdaptiveLayout,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = deviceHintIcon(adaptive),
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.fold_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** Icon khớp thiết bị / tư thế đang dùng (Fold, tablet, phone, desktop). */
private fun deviceHintIcon(adaptive: AdaptiveLayout): ImageVector {
    val fold: FoldInfo? = adaptive.fold
    return when {
        fold != null && (
            fold.isSeparating ||
                fold.isBookPosture ||
                fold.isTabletopPosture
            ) -> Icons.Rounded.DevicesFold
        fold != null -> Icons.Rounded.DevicesFold
        adaptive.widthClass == WindowWidthSizeClass.Expanded &&
            adaptive.heightClass == WindowHeightSizeClass.Compact -> Icons.Rounded.DesktopWindows
        adaptive.widthClass == WindowWidthSizeClass.Expanded ||
            adaptive.widthClass == WindowWidthSizeClass.Medium -> Icons.Rounded.TabletAndroid
        else -> Icons.Rounded.PhoneAndroid
    }
}

@Composable
private fun RecentRow(item: RecentFile, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface.copy(alpha = 0.92f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (item.kind == "pdf") Icons.Rounded.PictureAsPdf else Icons.Rounded.Architecture,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.kind.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
