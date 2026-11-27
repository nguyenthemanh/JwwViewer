package com.jwwviewer.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.Matrix
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
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * PDF viewer: base full-page bitmap (always pan/zoom via graphicsLayer).
 * After zoom settles, a sharp viewport overlay appears — hidden instantly on
 * the next gesture without recycling mid-frame (avoids jank / frozen zoom).
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
                        // Pager swipe steals pinch/pan once zoomed — disable while zoomed
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
    val density = LocalDensity.current
    var baseBmp by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var detailBmp by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var detailGen by remember(pageIndex) { mutableIntStateOf(0) }
    var pagePts by remember(pageIndex) { mutableStateOf(0 to 0) }
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }
    var viewW by remember { mutableFloatStateOf(0f) }
    var viewH by remember { mutableFloatStateOf(0f) }

    val quality = when {
        !highQuality -> 1.5f
        performance == PerformanceMode.LOW -> 2.0f
        performance == PerformanceMode.BALANCED -> 2.8f
        else -> 3.6f
    }.let { q -> if (largeScreen && highQuality) q + 0.4f else q }

    // Soft-hide: drop overlay from composition; recycle after the frame
    fun hideDetail() {
        val old = detailBmp
        if (old != null) {
            detailBmp = null
            deferRecycle(old)
        }
        detailGen++
    }

    LaunchedEffect(scale) {
        onZoomedChanged(scale > 1.02f)
    }

    // Release pager swipe lock when this page is no longer active
    LaunchedEffect(highQuality) {
        if (highQuality) onZoomedChanged(scale > 1.02f)
        else onZoomedChanged(false)
    }

    LaunchedEffect(fitNonce) {
        scale = 1f
        offset = Offset.Zero
        hideDetail()
        onZoomedChanged(false)
    }

    DisposableEffect(pageIndex) {
        onDispose {
            deferRecycle(baseBmp)
            deferRecycle(detailBmp)
        }
    }

    LaunchedEffect(pageIndex, viewW, viewH, quality) {
        if (viewW < 8f || viewH < 8f) return@LaunchedEffect
        val previous = baseBmp
        val held = arrayOfNulls<Bitmap>(1)
        try {
            val rendered = withContext(Dispatchers.Default) {
                val full = renderFullPage(session, pageIndex, viewW, viewH, quality)
                held[0] = full?.bitmap
                ensureActive()
                full
            }
            if (rendered != null) {
                pagePts = rendered.pageW to rendered.pageH
                baseBmp = rendered.bitmap
                held[0] = null
                if (previous != null && previous !== rendered.bitmap) deferRecycle(previous)
            }
        } catch (ce: CancellationException) {
            deferRecycle(held[0])
            throw ce
        }
    }

    // Sharp overlay only after zoom/pan idle — collectLatest cancels while still moving
    LaunchedEffect(pageIndex, highQuality, viewW, viewH) {
        if (!highQuality) {
            hideDetail()
            return@LaunchedEffect
        }
        snapshotFlow { Triple(scale, offset, pagePts) }
            .distinctUntilChanged()
            .collectLatest { (z, pan, pts) ->
                if (z < DETAIL_ZOOM_THRESHOLD) {
                    hideDetail()
                    return@collectLatest
                }
                kotlinx.coroutines.delay(SETTLE_MS)
                val pw = pts.first
                val ph = pts.second
                if (pw <= 0 || ph <= 0 || viewW < 8f) return@collectLatest
                val genAtStart = detailGen
                val held = arrayOfNulls<Bitmap>(1)
                try {
                    val rendered = withContext(Dispatchers.Default) {
                        val bmp = renderVisibleRegion(
                            session = session,
                            pageIndex = pageIndex,
                            pageW = pw,
                            pageH = ph,
                            viewW = viewW,
                            viewH = viewH,
                            zoom = z,
                            pan = pan,
                            quality = DETAIL_QUALITY,
                        )
                        held[0] = bmp
                        ensureActive()
                        bmp
                    }
                    if (genAtStart != detailGen) {
                        deferRecycle(rendered)
                        held[0] = null
                        return@collectLatest
                    }
                    val previous = detailBmp
                    detailBmp = rendered
                    held[0] = null
                    if (previous != null && previous !== detailBmp) deferRecycle(previous)
                } catch (ce: CancellationException) {
                    deferRecycle(held[0])
                    throw ce
                }
            }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(pageIndex) {
                // Single-finger pan at 1x is left unconsumed so HorizontalPager can swipe.
                // Pinch / zoomed pan are consumed and applied here (centroid-aware).
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
                            val zooming = kotlin.math.abs(zoomChange - 1f) > 0.01f
                            val panning = panChange.getDistance() > touchSlop
                            if (multi || scale > 1.02f) {
                                if (multi || zooming || panning) pastSlop = true
                            } else if (zooming && multi) {
                                pastSlop = true
                            } else {
                                // 1x single-finger — do not consume (pager swipe)
                                continue
                            }
                        }
                        if (pastSlop) {
                            event.changes.fastForEach {
                                if (it.positionChanged()) it.consume()
                            }
                            if (detailBmp != null) hideDetail()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val next = (scale * zoomChange).coerceIn(PDF_MIN_ZOOM, PDF_MAX_ZOOM)
                            val z = if (scale > 1e-4f) next / scale else 1f
                            val pivot = Offset(
                                centroid.x - size.width * 0.5f,
                                centroid.y - size.height * 0.5f,
                            )
                            // Keep content under pinch fixed; clamp so page stays partly on-screen
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

        val base = baseBmp
        if (base == null) {
            CircularProgressIndicator()
        } else {
            val baseImage = remember(base) { base.asImageBitmap() }
            Image(
                bitmap = baseImage,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            val detail = detailBmp
            if (detail != null && scale >= DETAIL_ZOOM_THRESHOLD) {
                val detailImage = remember(detail) { detail.asImageBitmap() }
                Image(
                    bitmap = detailImage,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Owns PdfRenderer + PFD. [closed] is checked under the same lock as render so
 * back-navigation cannot close while a page is mid-render (and vice versa).
 */
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

/**
 * Recycle after two Choreographer frames so RenderThread has finished the last
 * draw that still referenced this bitmap (Handler.post alone is too early).
 */
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

private const val PDF_MIN_ZOOM = 0.4f
private const val PDF_MAX_ZOOM = 32f
private const val DETAIL_ZOOM_THRESHOLD = 1.55f
private const val SETTLE_MS = 220L
/** Detail overlay quality multiplier (lower = less jank / memory). */
private const val DETAIL_QUALITY = 2.4f
private const val MAX_EDGE = 4096
private const val MAX_PIXELS = 12_000_000L

private data class FullPageBitmap(
    val bitmap: Bitmap,
    val pageW: Int,
    val pageH: Int,
)

private fun renderFullPage(
    session: PdfRenderSession,
    pageIndex: Int,
    viewW: Float,
    viewH: Float,
    quality: Float,
): FullPageBitmap? {
    synchronized(session.renderer) {
        if (session.closed) return null
        try {
            session.renderer.openPage(pageIndex).use { page ->
                val pageW = page.width.coerceAtLeast(1)
                val pageH = page.height.coerceAtLeast(1)
                val fit = min(viewW / pageW, viewH / pageH).coerceAtLeast(0.001f)
                var scale = fit * quality
                var w = ceil(pageW * scale).toInt().coerceAtLeast(1)
                var h = ceil(pageH * scale).toInt().coerceAtLeast(1)
                val limited = limitSize(w, h)
                w = limited.first
                h = limited.second
                scale = min(w.toFloat() / pageW, h.toFloat() / pageH)
                val bmp = allocArgb(w, h) ?: return null
                bmp.eraseColor(android.graphics.Color.WHITE)
                val matrix = Matrix().apply { setScale(scale, scale) }
                page.render(bmp, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return FullPageBitmap(bmp, pageW, pageH)
            }
        } catch (_: OutOfMemoryError) {
            return null
        } catch (_: Exception) {
            return null
        }
    }
}

private fun renderVisibleRegion(
    session: PdfRenderSession,
    pageIndex: Int,
    pageW: Int,
    pageH: Int,
    viewW: Float,
    viewH: Float,
    zoom: Float,
    pan: Offset,
    quality: Float,
): Bitmap? {
    synchronized(session.renderer) {
        if (session.closed) return null
        try {
            session.renderer.openPage(pageIndex).use { page ->
                val fit = min(viewW / pageW, viewH / pageH).coerceAtLeast(0.001f)
                val fittedW = pageW * fit
                val fittedH = pageH * fit
                val leftPad = (viewW - fittedW) / 2f
                val topPad = (viewH - fittedH) / 2f

                fun screenToFitted(sx: Float, sy: Float): Offset {
                    val cx = viewW / 2f
                    val cy = viewH / 2f
                    return Offset(
                        (sx - cx - pan.x) / zoom + cx,
                        (sy - cy - pan.y) / zoom + cy,
                    )
                }

                val tl = screenToFitted(0f, 0f)
                val br = screenToFitted(viewW, viewH)
                val visLeft = min(tl.x, br.x)
                val visTop = min(tl.y, br.y)
                val visRight = max(tl.x, br.x)
                val visBottom = max(tl.y, br.y)

                val pageLeft = ((visLeft - leftPad) / fit).coerceIn(0f, pageW.toFloat())
                val pageTop = ((visTop - topPad) / fit).coerceIn(0f, pageH.toFloat())
                val pageRight = ((visRight - leftPad) / fit).coerceIn(0f, pageW.toFloat())
                val pageBottom = ((visBottom - topPad) / fit).coerceIn(0f, pageH.toFloat())
                val regionW = (pageRight - pageLeft).coerceAtLeast(1f)
                val regionH = (pageBottom - pageTop).coerceAtLeast(1f)

                val outScale = (zoom * fit * quality).coerceAtLeast(1f)
                var bw = ceil(regionW * outScale).toInt().coerceAtLeast(1)
                var bh = ceil(regionH * outScale).toInt().coerceAtLeast(1)
                val limited = limitSize(bw, bh)
                bw = limited.first
                bh = limited.second
                val sx = bw / regionW
                val sy = bh / regionH

                val bmp = allocArgb(bw, bh) ?: return null
                bmp.eraseColor(android.graphics.Color.WHITE)
                val matrix = Matrix().apply {
                    postTranslate(-pageLeft, -pageTop)
                    postScale(sx, sy)
                }
                page.render(bmp, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp
            }
        } catch (_: OutOfMemoryError) {
            return null
        } catch (_: Exception) {
            return null
        }
    }
}

private fun limitSize(w: Int, h: Int): Pair<Int, Int> {
    var rw = w
    var rh = h
    val edge = max(rw, rh)
    if (edge > MAX_EDGE) {
        val k = MAX_EDGE.toFloat() / edge
        rw = (rw * k).roundToInt().coerceAtLeast(1)
        rh = (rh * k).roundToInt().coerceAtLeast(1)
    }
    val pixels = rw.toLong() * rh
    if (pixels > MAX_PIXELS) {
        val k = kotlin.math.sqrt(MAX_PIXELS.toDouble() / pixels).toFloat()
        rw = (rw * k).roundToInt().coerceAtLeast(1)
        rh = (rh * k).roundToInt().coerceAtLeast(1)
    }
    return rw to rh
}

private fun allocArgb(w: Int, h: Int): Bitmap? {
    return try {
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    } catch (_: OutOfMemoryError) {
        try {
            Bitmap.createBitmap(
                (w / 2).coerceAtLeast(1),
                (h / 2).coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        } catch (_: OutOfMemoryError) {
            null
        }
    }
}
