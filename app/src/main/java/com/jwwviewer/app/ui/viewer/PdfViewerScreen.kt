package com.jwwviewer.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * PDF viewer — architecture from MuPDF / Nutrient / Android PdfRenderer tiling docs:
 *
 * 1) Full-page **base** at screen-fit resolution (smooth pinch via graphicsLayer).
 * 2) After zoom settles, a **HQ patch** = only the visible viewport, rendered at
 *    screen pixels (optionally × detailDpi). Patch is drawn in view space (no
 *    FillBounds-over-page — that caused the white flash).
 * 3) Never Bitmap.recycle() while Compose may still draw; GC frees old bitmaps.
 * 4) Hide HQ instantly on the next gesture (base stays).
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
    var baseBmp by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var hqBmp by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var hqVisible by remember(pageIndex) { mutableStateOf(false) }
    var pagePts by remember(pageIndex) { mutableStateOf(0 to 0) }
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }
    var viewW by remember { mutableFloatStateOf(0f) }
    var viewH by remember { mutableFloatStateOf(0f) }
    var gesturing by remember { mutableStateOf(false) }
    var hqGen by remember(pageIndex) { mutableIntStateOf(0) }

    val baseQuality = when {
        !highQuality -> 1.4f
        performance == PerformanceMode.LOW -> 1.8f
        performance == PerformanceMode.BALANCED -> 2.4f
        else -> 3.0f
    }.let { q -> if (largeScreen && highQuality) q + 0.4f else q }

    // HQ patch dpi vs screen — like MuPDF “patch matches screen”; a bit oversample for CAD lines
    val hqOversample = when {
        !highQuality -> 1f
        performance == PerformanceMode.LOW -> 1.25f
        performance == PerformanceMode.BALANCED -> 1.75f
        else -> 2.25f
    }.let { o -> if (largeScreen && highQuality) o + 0.25f else o }

    LaunchedEffect(scale) {
        onZoomedChanged(scale > 1.02f)
    }

    LaunchedEffect(highQuality) {
        if (highQuality) onZoomedChanged(scale > 1.02f)
        else {
            onZoomedChanged(false)
            hqVisible = false
            hqBmp = null
        }
    }

    LaunchedEffect(fitNonce) {
        scale = 1f
        offset = Offset.Zero
        hqVisible = false
        hqBmp = null
        onZoomedChanged(false)
    }

    // Base full page @ viewport (MuPDF mEntire)
    LaunchedEffect(pageIndex, viewW, viewH, baseQuality, performance, highQuality) {
        if (viewW < 8f || viewH < 8f) return@LaunchedEffect
        try {
            val rendered = withContext(Dispatchers.Default) {
                renderFullPage(
                    session = session,
                    pageIndex = pageIndex,
                    viewW = viewW,
                    viewH = viewH,
                    quality = baseQuality,
                    performance = performance,
                    largeScreen = largeScreen && highQuality,
                )
            }
            if (rendered != null) {
                pagePts = rendered.pageW to rendered.pageH
                baseBmp = rendered.bitmap
            }
        } catch (ce: CancellationException) {
            throw ce
        }
    }

    // HQ visible patch after settle (MuPDF mPatch) — hide during gesture
    LaunchedEffect(pageIndex, highQuality) {
        if (!highQuality) return@LaunchedEffect
        snapshotFlow {
            HqKey(
                scale = (scale * 100).roundToInt(),
                ox = (offset.x / 8f).roundToInt(),
                oy = (offset.y / 8f).roundToInt(),
                vw = viewW.roundToInt(),
                vh = viewH.roundToInt(),
                gesturing = gesturing,
            )
        }
            .distinctUntilChanged()
            .collectLatest { key ->
                if (key.gesturing || key.scale <= 105 || key.vw < 8 || key.vh < 8) {
                    hqVisible = false
                    return@collectLatest
                }
                delay(SETTLE_MS)
                val gen = ++hqGen
                val s = scale
                val off = offset
                val pw = pagePts.first
                val ph = pagePts.second
                if (pw <= 0 || ph <= 0) return@collectLatest
                try {
                    val patch = withContext(Dispatchers.Default) {
                        renderViewportPatch(
                            session = session,
                            pageIndex = pageIndex,
                            pageW = pw,
                            pageH = ph,
                            viewW = viewW,
                            viewH = viewH,
                            scale = s,
                            offset = off,
                            oversample = hqOversample,
                            performance = performance,
                            largeScreen = largeScreen,
                        )
                    }
                    ensureActive()
                    if (gen != hqGen || session.closed) return@collectLatest
                    if (patch != null && !patch.isRecycled) {
                        hqBmp = patch
                        hqVisible = true
                    }
                } catch (ce: CancellationException) {
                    throw ce
                }
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
                            if (!gesturing) {
                                gesturing = true
                                hqVisible = false // MuPDF: drop HQ immediately on interact
                            }
                            event.changes.fastForEach {
                                if (it.positionChanged()) it.consume()
                            }
                            // Finger-up: centroid is Unspecified → NaN graphicsLayer kills hwui (Grok)
                            if (pressed.isEmpty()) continue
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (centroid == Offset.Unspecified ||
                                !centroid.x.isFinite() ||
                                !centroid.y.isFinite() ||
                                !zoomChange.isFinite() ||
                                !panChange.x.isFinite() ||
                                !panChange.y.isFinite()
                            ) {
                                continue
                            }
                            val next = (scale * zoomChange).coerceIn(PDF_MIN_ZOOM, PDF_MAX_ZOOM)
                            if (!next.isFinite()) continue
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
                            if (!newOffset.x.isFinite() || !newOffset.y.isFinite()) continue
                            scale = next
                            offset = newOffset
                        }
                    } while (event.changes.fastAny { it.pressed })
                    gesturing = false
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

        val base = baseBmp
        if (base == null || base.isRecycled) {
            CircularProgressIndicator()
        } else {
            val baseImage = remember(base) { base.asImageBitmap() }
            // Base: Fit + GPU pan/zoom (blurry when deep — intentional)
            Image(
                bitmap = baseImage,
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
            // HQ patch: already in view pixels — NO graphicsLayer (covers viewport)
            val hq = hqBmp
            if (hqVisible && hq != null && !hq.isRecycled && scale > 1.05f) {
                val hqImage = remember(hq) { hq.asImageBitmap() }
                Image(
                    bitmap = hqImage,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private data class HqKey(
    val scale: Int,
    val ox: Int,
    val oy: Int,
    val vw: Int,
    val vh: Int,
    val gesturing: Boolean,
)

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

private data class FullPageResult(
    val bitmap: Bitmap,
    val pageW: Int,
    val pageH: Int,
)

private const val PDF_MIN_ZOOM = 0.35f
private const val PDF_MAX_ZOOM = 40f
private const val PDF_MAX_EDGE = 4096
private const val PDF_MAX_PIXELS = 12_000_000
private const val SETTLE_MS = 120L

private fun limitWh(w0: Int, h0: Int): Pair<Int, Int> {
    var w = w0.coerceAtLeast(1)
    var h = h0.coerceAtLeast(1)
    val edge = max(w, h)
    if (edge > PDF_MAX_EDGE) {
        val k = PDF_MAX_EDGE.toFloat() / edge
        w = (w * k).toInt().coerceAtLeast(1)
        h = (h * k).toInt().coerceAtLeast(1)
    }
    val pixels = w.toLong() * h
    if (pixels > PDF_MAX_PIXELS) {
        val k = sqrt(PDF_MAX_PIXELS.toDouble() / pixels)
        w = (w * k).toInt().coerceAtLeast(1)
        h = (h * k).toInt().coerceAtLeast(1)
    }
    return w to h
}

/** Full page fitted into the view at [quality]× (MuPDF entire bitmap). */
private fun renderFullPage(
    session: PdfRenderSession,
    pageIndex: Int,
    viewW: Float,
    viewH: Float,
    quality: Float,
    performance: PerformanceMode,
    largeScreen: Boolean,
): FullPageResult? {
    synchronized(session.renderer) {
        if (session.closed) return null
        return try {
            session.renderer.openPage(pageIndex).use { page ->
                val pw = page.width
                val ph = page.height
                val fit = min(viewW / pw, viewH / ph)
                var w = ceil(pw * fit * quality).toInt().coerceAtLeast(1)
                var h = ceil(ph * fit * quality).toInt().coerceAtLeast(1)
                val lim = limitWh(w, h)
                w = lim.first
                h = lim.second
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
                FullPageResult(bmp, pw, ph)
            }
        } catch (_: OutOfMemoryError) {
            System.gc()
            null
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Visible viewport → page region → bitmap sized to view×oversample.
 * Matrix maps page points → patch pixels (Android PdfRenderer tile API).
 */
private fun renderViewportPatch(
    session: PdfRenderSession,
    pageIndex: Int,
    pageW: Int,
    pageH: Int,
    viewW: Float,
    viewH: Float,
    scale: Float,
    offset: Offset,
    oversample: Float,
    performance: PerformanceMode,
    largeScreen: Boolean,
): Bitmap? {
    if (viewW < 8f || viewH < 8f || pageW <= 0 || pageH <= 0) return null
    val fit = min(viewW / pageW, viewH / pageH)
    val drawnW = pageW * fit
    val drawnH = pageH * fit
    val left = (viewW - drawnW) / 2f
    val top = (viewH - drawnH) / 2f
    val cx = viewW * 0.5f
    val cy = viewH * 0.5f

    fun viewToPage(vx: Float, vy: Float): Offset {
        val lx = (vx - cx - offset.x) / scale + cx
        val ly = (vy - cy - offset.y) / scale + cy
        return Offset((lx - left) / fit, (ly - top) / fit)
    }

    // Exact viewport corners — pad would skew FillBounds alignment
    val tl = viewToPage(0f, 0f)
    val br = viewToPage(viewW, viewH)
    val pageLeft = min(tl.x, br.x).coerceIn(0f, pageW.toFloat())
    val pageTop = min(tl.y, br.y).coerceIn(0f, pageH.toFloat())
    val pageRight = max(tl.x, br.x).coerceIn(0f, pageW.toFloat())
    val pageBottom = max(tl.y, br.y).coerceIn(0f, pageH.toFloat())
    val regionW = (pageRight - pageLeft).coerceAtLeast(0.5f)
    val regionH = (pageBottom - pageTop).coerceAtLeast(0.5f)

    var bw = ceil(viewW * oversample).toInt().coerceAtLeast(1)
    var bh = ceil(viewH * oversample).toInt().coerceAtLeast(1)
    val lim = limitWh(bw, bh)
    bw = lim.first
    bh = lim.second

    val matrix = Matrix().apply {
        // page → patch pixels
        setScale(bw / regionW, bh / regionH)
        postTranslate(-pageLeft * (bw / regionW), -pageTop * (bh / regionH))
    }
    if (!matrix.isAffine) return null

    synchronized(session.renderer) {
        if (session.closed) return null
        return try {
            session.renderer.openPage(pageIndex).use { page ->
                val config = if (performance == PerformanceMode.LOW && !largeScreen) {
                    Bitmap.Config.RGB_565
                } else {
                    Bitmap.Config.ARGB_8888
                }
                val bmp = Bitmap.createBitmap(bw, bh, config)
                // White underpaint — only the rendered region is filled by PdfRenderer;
                // with a correct matrix the whole bitmap is covered by page content for
                // on-page views. Off-page pad stays white (page background).
                if (config == Bitmap.Config.ARGB_8888) {
                    bmp.eraseColor(android.graphics.Color.WHITE)
                }
                page.render(bmp, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        } catch (_: OutOfMemoryError) {
            System.gc()
            null
        } catch (_: Exception) {
            null
        }
    }
}
