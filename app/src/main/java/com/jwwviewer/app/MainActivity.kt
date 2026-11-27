package com.jwwviewer.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.jwwviewer.app.data.DrawingMemo
import com.jwwviewer.app.data.MemoStore
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
import com.jwwviewer.app.update.AutoUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
                            val partialLoad = stringResource(R.string.jww_partial_load)
                            val saveFailed = stringResource(R.string.save_on_exit_failed)
                            val dupOk = stringResource(R.string.file_action_duplicate_ok)
                            val dupFailFmt = stringResource(R.string.file_action_duplicate_fail)
                            val shareFail = stringResource(R.string.file_action_share_fail)
                            val recent by recentStore.recent.collectAsState(initial = emptyList())

                            var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                            var loading by remember { mutableStateOf(false) }
                            var error by remember { mutableStateOf<String?>(null) }
                            var drawing by remember { mutableStateOf<JwwDrawing?>(null) }
                            var title by remember { mutableStateOf("") }
                            var currentUri by remember { mutableStateOf<Uri?>(null) }
                            var memoKey by remember { mutableStateOf<String?>(null) }
                            var memos by remember { mutableStateOf<List<DrawingMemo>>(emptyList()) }
                            var memosBaseline by remember { mutableStateOf<List<DrawingMemo>>(emptyList()) }
                            var offerSaveOnExit by remember { mutableStateOf(false) }
                            var showSaveDialog by remember { mutableStateOf(false) }
                            var saving by remember { mutableStateOf(false) }
                            var saveError by remember { mutableStateOf<String?>(null) }
                            var openJob by remember { mutableStateOf<Job?>(null) }
                            val memoMutex = remember { Mutex() }
                            var memoSaveGen by remember { mutableIntStateOf(0) }

                            // Auto-check GitHub release on each app launch; download + install if newer.
                            LaunchedEffect(Unit) {
                                AutoUpdate.runOncePerProcess(context)
                            }

                            fun goHome() {
                                openJob?.cancel()
                                openJob = null
                                screen = Screen.Home
                                drawing = null
                                error = null
                                currentUri = null
                                memoKey = null
                                memos = emptyList()
                                memosBaseline = emptyList()
                                offerSaveOnExit = false
                                showSaveDialog = false
                                saveError = null
                                loading = false
                            }

                            fun persistMemos(list: List<DrawingMemo>) {
                                val key = memoKey ?: return
                                val gen = ++memoSaveGen
                                val snapshot = list.toList()
                                scope.launch(Dispatchers.IO) {
                                    memoMutex.withLock {
                                        if (gen != memoSaveGen) return@withLock
                                        runCatching { MemoStore.save(context, key, snapshot) }
                                    }
                                }
                            }

                            fun updateMemos(list: List<DrawingMemo>) {
                                memos = list
                                persistMemos(list)
                            }

                            fun requestExitViewer() {
                                // Memos already persist via MemoStore; only prompt for external-file copy.
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
                                openJob?.cancel()
                                val name = nameHint ?: queryDisplayName(uri) ?: "Document"
                                val kind = detectKind(name, uri)
                                title = name
                                currentUri = uri
                                offerSaveOnExit = fromExternal
                                memos = emptyList()
                                memosBaseline = emptyList()
                                memoKey = null
                                openJob = scope.launch {
                                    if (!fromExternal) {
                                        recentStore.add(uri, name, kind)
                                    }
                                    if (kind == "pdf") {
                                        drawing = null
                                        error = null
                                        screen = Screen.Pdf(uri)
                                        loading = false
                                        return@launch
                                    }
                                    loading = true
                                    error = null
                                    drawing = null
                                    screen = Screen.Jww
                                    val key = MemoStore.keyFor(uri.toString(), name)
                                    memoKey = key
                                    val loaded = withContext(Dispatchers.IO) {
                                        MemoStore.load(context, key)
                                    }
                                    memos = loaded
                                    memosBaseline = loaded
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            context.contentResolver.openInputStream(uri)?.use {
                                                JwwParser.readCapped(it)
                                            } ?: error("Cannot read file")
                                        }
                                    }
                                    if (!isActive) return@launch
                                    result.fold(
                                        onSuccess = { bytes ->
                                            val parsed = withContext(Dispatchers.Default) {
                                                runCatching { JwwParser.parse(bytes) }
                                            }
                                            if (!isActive) return@launch
                                            parsed.fold(
                                                onSuccess = {
                                                    drawing = it
                                                    error = null
                                                    if (it.truncated) {
                                                        Toast.makeText(
                                                            context,
                                                            partialLoad,
                                                            Toast.LENGTH_LONG,
                                                        ).show()
                                                    }
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
                                    onShareRecent = { item ->
                                        val uri = Uri.parse(item.uri)
                                        val mime = when (item.kind) {
                                            "pdf" -> "application/pdf"
                                            else -> "application/octet-stream"
                                        }
                                        try {
                                            val shareUri = shareableUri(context, uri)
                                            val send = Intent(Intent.ACTION_SEND).apply {
                                                type = mime
                                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                                putExtra(Intent.EXTRA_SUBJECT, item.name)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            startActivity(
                                                Intent.createChooser(
                                                    send,
                                                    getString(R.string.file_action_share),
                                                ),
                                            )
                                        } catch (_: Exception) {
                                            Toast.makeText(
                                                context,
                                                shareFail,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    },
                                    onDuplicateRecent = { item ->
                                        scope.launch {
                                            val src = Uri.parse(item.uri)
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    SavedFiles.saveCopy(
                                                        context,
                                                        src,
                                                        SavedFiles.duplicateName(item.name),
                                                    )
                                                }
                                            }
                                            result.fold(
                                                onSuccess = { saved ->
                                                    recentStore.add(
                                                        saved,
                                                        SavedFiles.duplicateName(item.name),
                                                        item.kind,
                                                    )
                                                    Toast.makeText(
                                                        context,
                                                        dupOk,
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                },
                                                onFailure = {
                                                    Toast.makeText(
                                                        context,
                                                        dupFailFmt.format(
                                                            it.message
                                                                ?: it.javaClass.simpleName,
                                                        ),
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                                },
                                            )
                                        }
                                    },
                                    onDeleteRecent = { item ->
                                        scope.launch {
                                            val uri = Uri.parse(item.uri)
                                            withContext(Dispatchers.IO) {
                                                SavedFiles.tryDelete(context, uri)
                                            }
                                            recentStore.remove(item.uri)
                                        }
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
                                    memos = memos,
                                    onMemosChange = { updateMemos(it) },
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
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return c.getString(idx)
                    }
                }
            uri.lastPathSegment
        } catch (_: Exception) {
            uri.lastPathSegment
        }
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

    /** Ensure share targets get a content URI with read grant. */
    private fun shareableUri(context: android.content.Context, uri: Uri): Uri {
        if (uri.scheme == "content") return uri
        if (uri.scheme == "file") {
            val path = uri.path ?: return uri
            val file = java.io.File(path)
            if (file.exists()) {
                return FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            }
        }
        return uri
    }

    private sealed interface Screen {
        data object Home : Screen
        data object Settings : Screen
        data object Jww : Screen
        data class Pdf(val uri: Uri) : Screen
    }
}
