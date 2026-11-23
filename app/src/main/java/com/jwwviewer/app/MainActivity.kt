package com.jwwviewer.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import com.jwwviewer.app.data.AppSettingsStore
import com.jwwviewer.app.data.RecentFile
import com.jwwviewer.app.data.RecentFilesStore
import com.jwwviewer.app.data.SavedFiles
import com.jwwviewer.app.parser.JwwDrawing
import com.jwwviewer.app.parser.JwwParser
import com.jwwviewer.app.ui.adaptive.rememberAdaptiveLayout
import com.jwwviewer.app.ui.home.HomeScreen
import com.jwwviewer.app.ui.settings.SettingsScreen
import com.jwwviewer.app.ui.theme.JwwViewerTheme
import com.jwwviewer.app.ui.viewer.JwwViewerScreen
import com.jwwviewer.app.ui.viewer.PdfViewerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var recentStore: RecentFilesStore
    private lateinit var settingsStore: AppSettingsStore

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        recentStore = RecentFilesStore(applicationContext)
        settingsStore = AppSettingsStore(applicationContext)

        val launchUri = intent?.data?.takeIf {
            intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_SEND
        }
        val launchName = launchUri?.let { queryDisplayName(it) }
        val openedFromOutside = launchUri != null

        setContent {
            val settingsState by settingsStore.settings.collectAsState(initial = null)
            val settings = settingsState
            if (settings == null) {
                Surface(modifier = Modifier.fillMaxSize()) {}
                return@setContent
            }
            val adaptive = rememberAdaptiveLayout()

            ProvideAppLocale(settings.language) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = density.density,
                        fontScale = density.fontScale * settings.fontScale,
                    ),
                ) {
                    JwwViewerTheme {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            val scope = rememberCoroutineScope()
                            val context = LocalContext.current
                            val errorOpen = stringResource(R.string.error_open)
                            val saveFailed = stringResource(R.string.save_on_exit_failed)
                            val recent by recentStore.recent.collectAsState(initial = emptyList())

                            var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                            var loading by remember { mutableStateOf(false) }
                            var error by remember { mutableStateOf<String?>(null) }
                            var drawing by remember { mutableStateOf<JwwDrawing?>(null) }
                            var title by remember { mutableStateOf("") }
                            var currentUri by remember { mutableStateOf<Uri?>(null) }
                            var offerSaveOnExit by remember { mutableStateOf(false) }
                            var showSaveDialog by remember { mutableStateOf(false) }
                            var saving by remember { mutableStateOf(false) }
                            var saveError by remember { mutableStateOf<String?>(null) }

                            fun goHome() {
                                screen = Screen.Home
                                drawing = null
                                error = null
                                currentUri = null
                                offerSaveOnExit = false
                                showSaveDialog = false
                                saveError = null
                            }

                            fun requestExitViewer() {
                                if (offerSaveOnExit && currentUri != null) {
                                    showSaveDialog = true
                                } else {
                                    goHome()
                                }
                            }

                            fun openUri(
                                uri: Uri,
                                nameHint: String? = null,
                                fromExternal: Boolean = false,
                            ) {
                                val name = nameHint ?: queryDisplayName(uri) ?: "Document"
                                val kind = detectKind(name, uri)
                                title = name
                                currentUri = uri
                                offerSaveOnExit = fromExternal
                                scope.launch {
                                    if (!fromExternal) {
                                        recentStore.add(uri, name, kind)
                                    }
                                    if (kind == "pdf") {
                                        drawing = null
                                        error = null
                                        screen = Screen.Pdf(uri)
                                        return@launch
                                    }
                                    loading = true
                                    error = null
                                    drawing = null
                                    screen = Screen.Jww
                                    val result = withContext(Dispatchers.Default) {
                                        runCatching {
                                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                                ?: error("Cannot read file")
                                        }
                                    }
                                    result.fold(
                                        onSuccess = { bytes ->
                                            val parsed = runCatching { JwwParser.parse(bytes) }
                                            parsed.fold(
                                                onSuccess = {
                                                    drawing = it
                                                    error = null
                                                },
                                                onFailure = {
                                                    error = it.message ?: errorOpen
                                                },
                                            )
                                        },
                                        onFailure = {
                                            error = it.message ?: errorOpen
                                        },
                                    )
                                    loading = false
                                }
                            }

                            val picker = rememberLauncherForActivityResult(
                                ActivityResultContracts.OpenDocument(),
                            ) { uri ->
                                if (uri != null) {
                                    try {
                                        contentResolver.takePersistableUriPermission(
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                        )
                                    } catch (_: SecurityException) {
                                    }
                                    openUri(uri, fromExternal = false)
                                }
                            }

                            LaunchedEffect(launchUri) {
                                if (launchUri != null) {
                                    openUri(launchUri, launchName, fromExternal = openedFromOutside)
                                }
                            }

                            val openPicker = {
                                picker.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "application/octet-stream",
                                        "*/*",
                                    ),
                                )
                            }

                            val viewing = screen is Screen.Jww || screen is Screen.Pdf
                            BackHandler(enabled = viewing || showSaveDialog) {
                                when {
                                    showSaveDialog -> showSaveDialog = false
                                    viewing -> requestExitViewer()
                                }
                            }

                            when (val s = screen) {
                                Screen.Home -> HomeScreen(
                                    recent = recent,
                                    adaptive = adaptive,
                                    onOpenFile = openPicker,
                                    onOpenRecent = { item: RecentFile ->
                                        openUri(Uri.parse(item.uri), item.name, fromExternal = false)
                                    },
                                    onSettings = { screen = Screen.Settings },
                                )
                                Screen.Settings -> SettingsScreen(
                                    settings = settings,
                                    adaptive = adaptive,
                                    onBack = { screen = Screen.Home },
                                    onLanguage = { lang ->
                                        scope.launch { settingsStore.setLanguage(lang) }
                                    },
                                    onPerformance = { mode ->
                                        scope.launch { settingsStore.setPerformance(mode) }
                                    },
                                    onTranslateEngine = { engine ->
                                        scope.launch { settingsStore.setTranslateEngine(engine) }
                                    },
                                    onFontScale = { v ->
                                        scope.launch { settingsStore.setFontScale(v) }
                                    },
                                    onStrokeScale = { v ->
                                        scope.launch { settingsStore.setStrokeScale(v) }
                                    },
                                    onFitPadding = { v ->
                                        scope.launch { settingsStore.setFitPaddingDp(v) }
                                    },
                                )
                                Screen.Jww -> JwwViewerScreen(
                                    title = title,
                                    drawing = drawing,
                                    loading = loading,
                                    error = error,
                                    settings = settings,
                                    onBack = { requestExitViewer() },
                                )
                                is Screen.Pdf -> PdfViewerScreen(
                                    title = title,
                                    uri = s.uri,
                                    settings = settings,
                                    largeScreen = adaptive.isExpanded || adaptive.useDualPane,
                                    onBack = { requestExitViewer() },
                                )
                            }

                            if (showSaveDialog) {
                                AlertDialog(
                                    onDismissRequest = {
                                        if (!saving) showSaveDialog = false
                                    },
                                    title = { Text(stringResource(R.string.save_on_exit_title)) },
                                    text = {
                                        Text(
                                            saveError
                                                ?: stringResource(R.string.save_on_exit_message, title),
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(
                                            enabled = !saving,
                                            onClick = {
                                                val src = currentUri ?: return@TextButton
                                                saving = true
                                                saveError = null
                                                scope.launch {
                                                    val result = withContext(Dispatchers.IO) {
                                                        runCatching {
                                                            SavedFiles.saveCopy(context, src, title)
                                                        }
                                                    }
                                                    result.fold(
                                                        onSuccess = { savedUri ->
                                                            val kind = detectKind(title, src)
                                                            recentStore.add(savedUri, title, kind)
                                                            saving = false
                                                            goHome()
                                                        },
                                                        onFailure = {
                                                            saving = false
                                                            saveError = it.message ?: saveFailed
                                                        },
                                                    )
                                                }
                                            },
                                        ) {
                                            Text(
                                                if (saving) {
                                                    stringResource(R.string.save_on_exit_saving)
                                                } else {
                                                    stringResource(R.string.save_on_exit_save)
                                                },
                                            )
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(
                                            enabled = !saving,
                                            onClick = { goHome() },
                                        ) {
                                            Text(stringResource(R.string.save_on_exit_discard))
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { recreate() }
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx)
                }
            }
        return uri.lastPathSegment
    }

    private fun detectKind(name: String, uri: Uri): String {
        val lower = name.lowercase()
        val path = (uri.path ?: "").lowercase()
        return when {
            lower.endsWith(".pdf") || path.endsWith(".pdf") -> "pdf"
            lower.endsWith(".jwc") || path.endsWith(".jwc") -> "jww"
            else -> "jww"
        }
    }

    private sealed interface Screen {
        data object Home : Screen
        data object Settings : Screen
        data object Jww : Screen
        data class Pdf(val uri: Uri) : Screen
    }
}
