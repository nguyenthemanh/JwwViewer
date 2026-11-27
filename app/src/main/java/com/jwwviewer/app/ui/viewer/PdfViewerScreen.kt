package com.jwwviewer.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.Choreographer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import com.jwwviewer.app.R
import com.jwwviewer.app.data.AppSettings
import com.jwwviewer.app.data.PerformanceMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * PDF viewer — sharpness model from 1.0.17-beta:
 * raster = page.width × pdfRasterScale, boost after zoom settles.
 * Fixes vs 1.0.17: no mid-pinch re-raster (jank), no immediate recycle (white/glitch),
 * centroid pinch, pager swipe at 1×.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfViewerScreen(
    title: String,
    uri: Uri,
    settings: AppSettings,
    largeScreen: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var pageCount by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<PdfRenderSession?>(null) }
    var fitNonce by remember { mutableIntStateOf(0) }
    var pageZoomed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val emptyPdf = stringResource(R.string.pdf_empty)
    val openFail = stringResource(R.string.pdf_open_failed)

    DisposableEffect(uri) {
        var created: PdfRenderSession? = null
        try {
            val fd = context.contentResolver.openFileDescriptor(uri, "r")
            if (fd != null) {
                created = PdfRenderSession(PdfRenderer(fd), fd)
                session = created
                pageCount = created.renderer.pageCount
                if (pageCount == 0) error = emptyPdf
            } else {
                error = openFail
            }
        } catch (e: Exception) {
            created?.closeQuietly()
            session = null
            error = e.message?.takeIf { it.isNotBlank() } ?: openFail
        }
        onDispose {
            val s = session
            session = null
            s?.closeQuietly()
        }
    }

    val pagerState = rememberPagerState(pageCount = { pageCount.coerceAtLeast(1) })
    val canPrev = pagerState.currentPage > 0
    val canNext = pageCount > 0 && pagerState.currentPage < pageCount - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Surface(
            color = Color.White,
            tonalElevation = 1.dp,
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
                    compact = true,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 0.dp)
                        .padding(horizontal = 4.dp),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (pageCount > 0) {
                        Text(
                            text = stringResource(
                                R.string.tool_page,
                                pagerState.currentPage + 1,
                                pageCount,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    ViewerToolButton(
                        icon = Icons.AutoMirrored.Rounded.NavigateBefore,
                        label = stringResource(R.string.tool_prev),
                        compact = true,
                        onClick = {
                            if (canPrev) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        },
                    )
                    ViewerToolButton(
                        icon = Icons.AutoMirrored.Rounded.NavigateNext,
                        label = stringResource(R.string.tool_next),
                        compact = true,
                        onClick = {
                            if (canNext) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                    )
                    ViewerToolButton(
                        icon = Icons.Rounded.ZoomOutMap,
                        label = stringResource(R.string.tool_fit),
                        compact = true,
                        onClick = {
                            fitNonce++
                            pageZoomed = false
                        },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White),
        ) {
            when {
                error != null -> Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
                session == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else -> {
                    val pdfSession = session!!
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 0,
                        userScrollEnabled = !pageZoomed,
                    ) { page ->
                        val active = page == pagerState.currentPage
                        PdfPage(
                            session = pdfSession,
                            pageIndex = page,
                            performance = settings.performance,
                            largeScreen = largeScreen,
                            fitNonce = fitNonce,
                            highQuality = active,
                            onZoomedChanged = { zoomed ->
                                if (active) pageZoomed = zoomed
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(
    session: PdfRenderSession,
    pageIndex: Int,
    performance: PerformanceMode,
    largeScreen: Boolean,
    fitNonce: Int,
    highQuality: Boolean,
    onZoomedChanged: (Boolean) -> Unit,
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }
    var viewW by remember { mutableFloatStateOf(0f) }
    var viewH by remember { mutableFloatStateOf(0f) }
    // Applied raster — updated only after zoom settles (1.0.17 boost, without mid-pinch jank)
    var appliedRaster by remember(pageIndex) { mutableFloatStateOf(0f) }

    val baseRaster = if (!highQuality) {
        2f
    } else if (largeScreen) {
        maxOf(performance.pdfRasterScale, 4f)
    } else {
        performance.pdfRasterScale
    }

    fun boostFor(z: Float): Float = when {
        z >= 12f -> min(baseRaster * 2.2f, 12f)
        z >= 6f -> min(baseRaster * 1.7f, 10f)
        z >= 3f -> min(baseRaster * 1.35f, 8f)
        else -> baseRaster
    }

    LaunchedEffect(pageIndex, baseRaster) {
        if (appliedRaster < 0.1f) appliedRaster = baseRaster
    }

    LaunchedEffect(scale) {
        onZoomedChanged(scale > 1.02f)
    }

    LaunchedEffect(highQuality) {
        if (highQuality) onZoomedChanged(scale > 1.02f)
        else onZoomedChanged(false)
    }

    LaunchedEffect(fitNonce) {
        scale = 1f
        offset = Offset.Zero
        appliedRaster = baseRaster
        onZoomedChanged(false)
    }

    DisposableEffect(pageIndex) {
        onDispose { deferRecycle(bitmap) }
    }

    // Settle before boost — this is what removes 1.0.17 mid-pinch stutter
    LaunchedEffect(pageIndex, highQuality, baseRaster) {
        if (!highQuality) {
            appliedRaster = baseRaster
            return@LaunchedEffect
        }
        snapshotFlow { scale }
            .map { boostFor(it) }
            .distinctUntilChanged { a, b -> abs(a - b) < 0.05f }
            .collectLatest { target ->
                if (target > baseRaster + 0.05f) delay(SETTLE_MS)
                appliedRaster = target
            }
    }

    LaunchedEffect(pageIndex, appliedRaster, performance, largeScreen, highQuality) {
        if (appliedRaster < 0.1f) return@LaunchedEffect
        val previous = bitmap
        val held = arrayOfNulls<Bitmap>(1)
        try {
            val rendered = withContext(Dispatchers.Default) {
                val bmp = renderPdfPage(
                    session = session,
                    pageIndex = pageIndex,
                    raster = appliedRaster,
                    performance = performance,
                    largeScreen = largeScreen && highQuality,
                )
                held[0] = bmp
                ensureActive()
                bmp
            }
            if (rendered != null) {
                bitmap = rendered
                held[0] = null
                if (previous != null && previous !== rendered) deferRecycle(previous)
            }
        } catch (ce: CancellationException) {
            deferRecycle(held[0])
            throw ce
        }
    }

    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(pageIndex) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pastSlop = false
                    val touchSlop = viewConfiguration.touchSlop
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val multi = pressed.size > 1
                        if (!pastSlop) {
                            val zooming = abs(zoomChange - 1f) > 0.01f
                            val panning = panChange.getDistance() > touchSlop
                            if (multi || scale > 1.02f) {
                                if (multi || zooming || panning) pastSlop = true
                            } else {
                                continue
                            }
                        }
                        if (pastSlop) {
                            event.changes.fastForEach {
                                if (it.positionChanged()) it.consume()
                            }
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val next = (scale * zoomChange).coerceIn(PDF_MIN_ZOOM, PDF_MAX_ZOOM)
                            val z = if (scale > 1e-4f) next / scale else 1f
                            val pivot = Offset(
                                centroid.x - size.width * 0.5f,
                                centroid.y - size.height * 0.5f,
                            )
                            var newOffset = offset * z + pivot * (1f - z) + panChange
                            val maxPanX = max(0f, (viewW * next - viewW) / 2f)
                            val maxPanY = max(0f, (viewH * next - viewH) / 2f)
                            newOffset = Offset(
                                newOffset.x.coerceIn(-maxPanX, maxPanX),
                                newOffset.y.coerceIn(-maxPanY, maxPanY),
                            )
                            scale = next
                            offset = newOffset
                        }
                    } while (event.changes.fastAny { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        LaunchedEffect(wPx, hPx) {
            viewW = wPx
            viewH = hPx
        }

        val bmp = bitmap
        if (bmp == null || bmp.isRecycled) {
            CircularProgressIndicator()
        } else {
            val image = remember(bmp) { bmp.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
    }
}

private class PdfRenderSession(
    val renderer: PdfRenderer,
    private val pfd: ParcelFileDescriptor,
) {
    @Volatile
    var closed: Boolean = false
        private set

    fun closeQuietly() {
        synchronized(renderer) {
            if (closed) return
            closed = true
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
    }
}

private fun deferRecycle(bmp: Bitmap?) {
    if (bmp == null || bmp.isRecycled) return
    val choreographer = Choreographer.getInstance()
    choreographer.postFrameCallback {
        choreographer.postFrameCallback {
            if (!bmp.isRecycled) {
                runCatching { bmp.recycle() }
            }
        }
    }
}

private const val PDF_MIN_ZOOM = 0.35f
private const val PDF_MAX_ZOOM = 40f
private const val PDF_MAX_BITMAP_EDGE = 8192
private const val SETTLE_MS = 160L

/** Same raster path as 1.0.17-beta: page.width × raster (not viewport-fit). */
private fun renderPdfPage(
    session: PdfRenderSession,
    pageIndex: Int,
    raster: Float,
    performance: PerformanceMode,
    largeScreen: Boolean,
): Bitmap? {
    synchronized(session.renderer) {
        if (session.closed) return null
        try {
            session.renderer.openPage(pageIndex).use { page ->
                var w = (page.width * raster).toInt().coerceAtLeast(1)
                var h = (page.height * raster).toInt().coerceAtLeast(1)
                val maxEdge = max(w, h)
                if (maxEdge > PDF_MAX_BITMAP_EDGE) {
                    val k = PDF_MAX_BITMAP_EDGE.toFloat() / maxEdge
                    w = (w * k).toInt().coerceAtLeast(1)
                    h = (h * k).toInt().coerceAtLeast(1)
                }
                val config = if (performance == PerformanceMode.LOW && !largeScreen) {
                    Bitmap.Config.RGB_565
                } else {
                    Bitmap.Config.ARGB_8888
                }
                val bmp = Bitmap.createBitmap(w, h, config)
                if (config == Bitmap.Config.ARGB_8888) {
                    bmp.eraseColor(android.graphics.Color.WHITE)
                }
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp
            }
        } catch (_: OutOfMemoryError) {
            return null
        } catch (_: Exception) {
            return null
        }
    }
}
