package com.jwwviewer.app.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GTranslate
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jwwviewer.app.R
import kotlin.math.hypot
import kotlin.math.roundToInt
import com.jwwviewer.app.data.AppLanguage
import com.jwwviewer.app.data.AppSettings
import com.jwwviewer.app.data.DrawingMemo
import com.jwwviewer.app.data.TranslateEngine
import com.jwwviewer.app.parser.JwwDrawing
import com.jwwviewer.app.render.CadRenderer
import com.jwwviewer.app.render.CadTextHit
import com.jwwviewer.app.render.CadTextHitTester
import com.jwwviewer.app.render.JwwColors
import com.jwwviewer.app.render.ViewTransform
import com.jwwviewer.app.translate.JaTranslator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JwwViewerScreen(
    title: String,
    drawing: JwwDrawing?,
    loading: Boolean,
    error: String?,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val paper = if (isDark) JwwColors.paperDark else JwwColors.paperLight
    val renderer = remember { CadRenderer() }
    var scale by remember(drawing, settings.fitPaddingDp) { mutableFloatStateOf(1f) }
    var offset by remember(drawing, settings.fitPaddingDp) { mutableStateOf(Offset.Zero) }
    var fitted by remember(drawing, settings.fitPaddingDp) { mutableStateOf(false) }
    var rotationDeg by remember(drawing) { mutableFloatStateOf(0f) }
    var showLayers by remember { mutableStateOf(false) }
    var showMemoPanel by remember { mutableStateOf(false) }
    var placeMemoMode by remember { mutableStateOf(false) }
    var measureMode by remember { mutableStateOf(false) }
    var measureA by remember(drawing) { mutableStateOf<Offset?>(null) }
    var measureB by remember(drawing) { mutableStateOf<Offset?>(null) }
    var memos by remember(drawing) { mutableStateOf<List<DrawingMemo>>(emptyList()) }
    var editingMemo by remember { mutableStateOf<DrawingMemo?>(null) }
    var selectedHit by remember { mutableStateOf<CadTextHit?>(null) }
    val layerVisible = remember(drawing) {
        BooleanArray(256) { i ->
            drawing?.layerStates?.getOrElse(i) { 1 } != 0
        }
    }
    var layerTick by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(paper),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 2.dp,
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 2.dp, end = 4.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewerToolButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    label = stringResource(R.string.tool_back),
                    onClick = onBack,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                )
                ViewerToolButton(
                    icon = Icons.Rounded.ScreenRotation,
                    label = stringResource(R.string.tool_rotate),
                    onClick = {
                        rotationDeg = (rotationDeg + 90f) % 360f
                        fitted = false
                        offset = Offset.Zero
                    },
                )
                ViewerToolButton(
                    icon = Icons.Rounded.ZoomOutMap,
                    label = stringResource(R.string.tool_fit),
                    onClick = {
                        fitted = false
                        offset = Offset.Zero
                    },
                )
                ViewerToolButton(
                    icon = Icons.Rounded.Straighten,
                    label = stringResource(R.string.tool_measure),
                    onClick = {
                        measureMode = !measureMode
                        if (!measureMode) {
                            measureA = null
                            measureB = null
                        } else {
                            placeMemoMode = false
                            measureA = null
                            measureB = null
                        }
                    },
                )
                ViewerToolButton(
                    icon = Icons.AutoMirrored.Rounded.StickyNote2,
                    label = stringResource(R.string.tool_memo),
                    onClick = { showMemoPanel = true },
                )
                ViewerToolButton(
                    icon = Icons.Rounded.Layers,
                    label = stringResource(R.string.tool_layers),
                    onClick = { showLayers = true },
                )
            }
        }

        if (placeMemoMode) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.memo_place_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { placeMemoMode = false }) {
                        Text(stringResource(R.string.memo_cancel_place))
                    }
                }
            }
        }

        if (measureMode) {
            val a = measureA
            val b = measureB
            val scaleS = drawing?.activeScale ?: 1.0
            val distWorld = if (a != null && b != null) {
                hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
            } else {
                null
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = when {
                            distWorld != null -> stringResource(
                                R.string.measure_result,
                                formatMeasure(distWorld),
                                formatScaleLabel(scaleS),
                            )
                            a != null -> stringResource(R.string.measure_pick_b)
                            else -> stringResource(R.string.measure_pick_a)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            measureA = null
                            measureB = null
                        },
                    ) {
                        Text(stringResource(R.string.measure_reset))
                    }
                    TextButton(
                        onClick = {
                            measureMode = false
                            measureA = null
                            measureB = null
                        },
                    ) {
                        Text(stringResource(R.string.memo_cancel_place))
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(paper),
        ) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null -> Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
                drawing != null -> {
                    val bounds = drawing.bounds
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.02f, 80f)
                                    offset += pan
                                }
                            }
                            .pointerInput(
                                drawing,
                                scale,
                                offset,
                                rotationDeg,
                                layerTick,
                                settings.fontScale,
                                settings.performance.drawTexts,
                                placeMemoMode,
                                measureMode,
                                memos,
                            ) {
                                detectTapGestures { tap ->
                                    val b = bounds ?: return@detectTapGestures
                                    val vw = size.width.toFloat()
                                    val vh = size.height.toFloat()
                                    val vt = ViewTransform(
                                        b, vw, vh, scale, offset.x, offset.y, rotationDeg,
                                    )

                                    if (measureMode) {
                                        val world = vt.screenToWorld(tap.x, tap.y)
                                        when {
                                            measureA == null || measureB != null -> {
                                                measureA = world
                                                measureB = null
                                            }
                                            else -> measureB = world
                                        }
                                        return@detectTapGestures
                                    }

                                    val memoHit = CadTextHitTester.hitMemo(
                                        memos = memos,
                                        tap = tap,
                                        bounds = b,
                                        viewW = vw,
                                        viewH = vh,
                                        scale = scale,
                                        offsetX = offset.x,
                                        offsetY = offset.y,
                                        fontScale = settings.fontScale,
                                        rotationDeg = rotationDeg,
                                    )
                                    if (memoHit != null) {
                                        editingMemo = memoHit
                                        placeMemoMode = false
                                        return@detectTapGestures
                                    }

                                    if (placeMemoMode) {
                                        val world = vt.screenToWorld(tap.x, tap.y)
                                        val created = DrawingMemo(
                                            x = world.x,
                                            y = world.y,
                                            text = "",
                                        )
                                        memos = memos + created
                                        editingMemo = created
                                        placeMemoMode = false
                                        return@detectTapGestures
                                    }

                                    if (!settings.performance.drawTexts) return@detectTapGestures
                                    val hit = CadTextHitTester.hitJapanese(
                                        drawing = drawing,
                                        layerVisible = layerVisible,
                                        tap = tap,
                                        bounds = b,
                                        viewW = vw,
                                        viewH = vh,
                                        scale = scale,
                                        offsetX = offset.x,
                                        offsetY = offset.y,
                                        fontScale = settings.fontScale,
                                        rotationDeg = rotationDeg,
                                    )
                                    if (hit != null) selectedHit = hit
                                }
                            },
                    ) {
                        if (bounds != null) {
                            if (!fitted) {
                                val pad = with(density) { settings.fitPaddingDp.dp.toPx() }
                                scale = CadRenderer.fitScale(
                                    bounds, size.width, size.height, pad, rotationDeg,
                                )
                                offset = Offset.Zero
                                fitted = true
                            }
                            layerTick
                            renderer.prepare(drawing, layerVisible)
                            renderer.draw(
                                scope = this,
                                bounds = bounds,
                                viewW = size.width,
                                viewH = size.height,
                                scale = scale,
                                offsetX = offset.x,
                                offsetY = offset.y,
                                strokePx = with(density) { (1.25f * settings.strokeScale).dp.toPx() },
                                fontScale = settings.fontScale,
                                performance = settings.performance,
                                rotationDeg = rotationDeg,
                                memos = memos,
                            )
                            val a = measureA
                            val b = measureB
                            if (measureMode && a != null) {
                                val vt = ViewTransform(
                                    bounds,
                                    size.width.toFloat(),
                                    size.height.toFloat(),
                                    scale,
                                    offset.x,
                                    offset.y,
                                    rotationDeg,
                                )
                                val sa = vt.worldToScreen(a.x, a.y)
                                val measureColor = Color(0xFFE65100)
                                drawCircle(measureColor, radius = 8f, center = sa)
                                drawCircle(
                                    Color.White,
                                    radius = 8f,
                                    center = sa,
                                    style = Stroke(width = 2f),
                                )
                                if (b != null) {
                                    val sb = vt.worldToScreen(b.x, b.y)
                                    drawLine(
                                        color = measureColor,
                                        start = sa,
                                        end = sb,
                                        strokeWidth = 3f,
                                        cap = StrokeCap.Round,
                                    )
                                    drawCircle(measureColor, radius = 8f, center = sb)
                                    drawCircle(
                                        Color.White,
                                        radius = 8f,
                                        center = sb,
                                        style = Stroke(width = 2f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedHit?.let { hit ->
        TranslateNoteSheet(
            original = hit.display,
            language = settings.language,
            engine = settings.translateEngine,
            onDismiss = { selectedHit = null },
        )
    }

    editingMemo?.let { memo ->
        MemoEditSheet(
            memo = memo,
            onSave = { text ->
                val trimmed = text.trim()
                memos = if (trimmed.isEmpty()) {
                    memos.filter { it.id != memo.id }
                } else {
                    memos.map { if (it.id == memo.id) it.copy(text = trimmed) else it }
                }
                editingMemo = null
            },
            onDelete = {
                memos = memos.filter { it.id != memo.id }
                editingMemo = null
            },
            onDismiss = {
                if (memo.text.isBlank()) {
                    memos = memos.filter { it.id != memo.id }
                }
                editingMemo = null
            },
        )
    }

    if (showMemoPanel && drawing != null) {
        MemoToolsSheet(
            fileMemo = drawing.memo,
            memos = memos,
            placeMemoMode = placeMemoMode,
            onTogglePlace = {
                placeMemoMode = !placeMemoMode
                if (placeMemoMode) {
                    measureMode = false
                    measureA = null
                    measureB = null
                }
                showMemoPanel = false
            },
            onOpenMemo = { editingMemo = it; showMemoPanel = false },
            onDeleteMemo = { id -> memos = memos.filter { it.id != id } },
            onDismiss = { showMemoPanel = false },
        )
    }

    if (showLayers && drawing != null) {
        val used = remember(drawing) {
            drawing.elements
                .map { it.layerIndex.coerceIn(0, 255) }
                .distinct()
                .sorted()
        }
        ModalBottomSheet(
            onDismissRequest = { showLayers = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                text = stringResource(R.string.layers),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(used) { idx ->
                    val name = drawing.layerNames.getOrElse(idx) { "" }
                        .ifBlank { "Layer ${idx / 16}-${idx % 16}" }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        Checkbox(
                            checked = layerVisible[idx],
                            onCheckedChange = {
                                layerVisible[idx] = it
                                layerTick++
                            },
                        )
                        Column {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "G${idx / 16} / L${idx % 16}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoToolsSheet(
    fileMemo: String,
    memos: List<DrawingMemo>,
    placeMemoMode: Boolean,
    onTogglePlace: () -> Unit,
    onOpenMemo: (DrawingMemo) -> Unit,
    onDeleteMemo: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.memo_tools),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.memo_tools_hint),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.memo_file),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = fileMemo.ifBlank { stringResource(R.string.memo_file_empty) },
                style = MaterialTheme.typography.bodyLarge,
                color = if (fileMemo.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.memo_pins, memos.size),
                    style = MaterialTheme.typography.titleLarge,
                )
                FilterChip(
                    selected = placeMemoMode,
                    onClick = onTogglePlace,
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.AddComment,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    label = { Text(stringResource(R.string.memo_add)) },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (memos.isEmpty()) {
                Text(
                    text = stringResource(R.string.memo_pins_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    itemsIndexed(memos, key = { _, m -> m.id }) { index, memo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(
                                text = memo.text.ifBlank { stringResource(R.string.memo_untitled) },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onOpenMemo(memo) }) {
                                Icon(
                                    Icons.Rounded.EditNote,
                                    contentDescription = stringResource(R.string.memo_edit),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = { onDeleteMemo(memo.id) }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.memo_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoEditSheet(
    memo: DrawingMemo,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(memo.id) { mutableStateOf(memo.text) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.memo_edit),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
                placeholder = { Text(stringResource(R.string.memo_placeholder)) },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.memo_delete))
                }
                Spacer(modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = onDismiss) {
                    Text(stringResource(R.string.memo_cancel_place))
                }
                Button(onClick = { onSave(draft) }) {
                    Text(stringResource(R.string.memo_save))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslateNoteSheet(
    original: String,
    language: AppLanguage,
    engine: TranslateEngine,
    onDismiss: () -> Unit,
) {
    val target = remember(language) { JaTranslator.targetFor(language) }
    var activeEngine by remember(engine) { mutableStateOf(engine) }
    var translated by remember(original, target, activeEngine) { mutableStateOf<String?>(null) }
    var translating by remember(original, target, activeEngine) { mutableStateOf(false) }
    var translateError by remember(original, target, activeEngine) { mutableStateOf<String?>(null) }
    val downloadHint = stringResource(R.string.translate_downloading)
    val failHint = stringResource(R.string.translate_failed)

    LaunchedEffect(original, target, activeEngine) {
        if (target == null) {
            translated = null
            translating = false
            translateError = null
            return@LaunchedEffect
        }
        translating = true
        translateError = null
        translated = null
        runCatching {
            JaTranslator.translateJa(original, target, activeEngine)
        }.onSuccess {
            translated = it
            translating = false
        }.onFailure {
            translateError = it.message?.takeIf { m -> m.isNotBlank() } ?: failHint
            translating = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Rounded.GTranslate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    text = stringResource(R.string.translate_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.translate_hint),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.translate_original),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = original, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.translate_result),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            when {
                target == null -> Text(
                    text = stringResource(R.string.translate_same_ja),
                    style = MaterialTheme.typography.bodyLarge,
                )
                translating -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = downloadHint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                translateError != null -> Text(
                    text = translateError!!,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Text(
                    text = translated.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private fun Color.luminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Format CAD world distance (JWW coords ≈ mm thực). */
private fun formatMeasure(mm: Double): String {
    val abs = kotlin.math.abs(mm)
    return when {
        abs >= 1000.0 -> String.format("%.3f m / %.1f mm", mm / 1000.0, mm)
        abs >= 10.0 -> String.format("%.1f mm", mm)
        else -> String.format("%.3f mm", mm)
    }
}

private fun formatScaleLabel(scale: Double): String {
    val n = if (kotlin.math.abs(scale - scale.roundToInt()) < 1e-6) {
        scale.roundToInt().toString()
    } else {
        String.format("%.3f", scale)
    }
    return n
}
