package com.jwwviewer.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jwwviewer.app.R
import com.jwwviewer.app.data.AppSettings
import com.jwwviewer.app.data.PerformanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var fitNonce by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val emptyPdf = stringResource(R.string.pdf_empty)
    val openFail = stringResource(R.string.pdf_open_failed)

    DisposableEffect(uri) {
        try {
            val fd = context.contentResolver.openFileDescriptor(uri, "r")
            pfd = fd
            renderer = fd?.let { PdfRenderer(it) }
            pageCount = renderer?.pageCount ?: 0
            if (pageCount == 0) error = emptyPdf
        } catch (e: Exception) {
            error = e.message?.takeIf { it.isNotBlank() } ?: openFail
        }
        onDispose {
            renderer?.close()
            pfd?.close()
            renderer = null
            pfd = null
        }
    }

    val pagerState = rememberPagerState(pageCount = { pageCount.coerceAtLeast(1) })
    val canPrev = pagerState.currentPage > 0
    val canNext = pageCount > 0 && pagerState.currentPage < pageCount - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 2.dp,
            shadowElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 2.dp, end = 4.dp, top = 2.dp, bottom = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
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
                            modifier = Modifier.padding(horizontal = 8.dp),
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
                        onClick = { fitNonce++ },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                error != null -> Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
                renderer == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else -> {
                    val pdf = renderer!!
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        PdfPage(
                            renderer = pdf,
                            pageIndex = page,
                            performance = settings.performance,
                            largeScreen = largeScreen,
                            fitNonce = fitNonce,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(
    renderer: PdfRenderer,
    pageIndex: Int,
    performance: PerformanceMode,
    largeScreen: Boolean,
    fitNonce: Int,
) {
    var bitmap by remember(pageIndex, performance, largeScreen) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }
    val raster = if (largeScreen) {
        maxOf(performance.pdfRasterScale, 2.5f)
    } else {
        performance.pdfRasterScale
    }

    LaunchedEffect(fitNonce) {
        scale = 1f
        offset = Offset.Zero
    }

    LaunchedEffect(pageIndex, renderer, performance, largeScreen) {
        bitmap = withContext(Dispatchers.Default) {
            synchronized(renderer) {
                renderer.openPage(pageIndex).use { page ->
                    val w = (page.width * raster).toInt().coerceAtLeast(1)
                    val h = (page.height * raster).toInt().coerceAtLeast(1)
                    val config = if (performance == PerformanceMode.LOW && !largeScreen) {
                        Bitmap.Config.RGB_565
                    } else {
                        Bitmap.Config.ARGB_8888
                    }
                    val bmp = Bitmap.createBitmap(w, h, config)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
        scale = 1f
        offset = Offset.Zero
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(pageIndex) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset += pan
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
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
