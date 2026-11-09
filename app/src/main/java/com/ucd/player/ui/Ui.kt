package com.ucd.player.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.content.Intent
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import dev.chrisbanes.haze.HazeState
import kotlin.math.PI
import kotlin.math.sqrt
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithCache
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.res.Configuration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ucd.player.data.*
import com.ucd.player.player.PlayerActivity
import com.ucd.player.ui.theme.GlassAlertDialog
import com.ucd.player.ui.theme.LocalUcdStyle
import com.ucd.player.ui.theme.glassPanel
import com.ucd.player.ui.theme.glassCapsule
import com.ucd.player.ui.theme.GlassMenuItem
import com.ucd.player.ui.theme.rs
import com.ucd.player.ui.theme.GlassBackdrop
import com.ucd.player.ui.theme.BackdropArt
import com.ucd.player.ui.theme.glassBadge
import androidx.compose.ui.graphics.asAndroidBitmap
import com.ucd.player.ui.theme.glassOnImage
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.ucd.player.ui.theme.GlassDropdownMenu
import com.ucd.player.ui.theme.GlassIconButton
import com.ucd.player.ui.theme.glass
import com.ucd.player.ui.theme.glassBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UcdRoot(db: UcdDatabase, resumeTick: Int = 0, themeId: String = "midnight", onTheme: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val dao = remember { db.playlistDao() }
    val prefs = remember { UcdPrefs(ctx) }
    val scope = rememberCoroutineScope()

    val videoPerms = remember {
        when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    fun hasAccess() = videoPerms.any {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(hasAccess()) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAccess()
        if (granted && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(Unit) {
        if (!granted) videoLauncher.launch(videoPerms)
        else if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(resumeTick) { granted = hasAccess() }

    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var reloadTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(granted, resumeTick, reloadTick) {
        if (granted) {
            val fresh = MediaStoreScanner(ctx).scan()
            if (fresh != videos) videos = fresh
        }
    }

    val playlistsFlow = remember { dao.playlists() }
    val playlists by playlistsFlow.collectAsState(initial = emptyList())

    var tab by remember { mutableIntStateOf(0) }
    var folder by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<PlaylistEntity?>(null) }
    var addTargets by remember { mutableStateOf<List<VideoItem>?>(null) }
    var videoMenu by remember { mutableStateOf<VideoItem?>(null) }
    var renameTarget by remember { mutableStateOf<VideoItem?>(null) }
    var moveTargets by remember { mutableStateOf<List<VideoItem>?>(null) }
    var deleteVideoTargets by remember { mutableStateOf<List<VideoItem>?>(null) }
    // Selección de varios videos (se activa manteniendo pulsado un video)
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var visibleVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    val selecting = selected.isNotEmpty()
    // Lo que hay que terminar cuando el usuario acepta la ventana de permiso de Android
    var pendingGrant by remember { mutableStateOf<(() -> Unit)?>(null) }
    val grantLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val next = pendingGrant
        pendingGrant = null
        if (r.resultCode == android.app.Activity.RESULT_OK) next?.invoke()
    }
    /** Ejecuta una operación sobre un video; si Android pide permiso, lo pide y la repite. */
    fun runOp(op: () -> VideoOps.Result, retryAfterGrant: Boolean = true, done: () -> Unit = {}) {
        when (val r = op()) {
            is VideoOps.Result.Ok -> { done(); reloadTick++ }
            is VideoOps.Result.NeedsGrant -> {
                pendingGrant = { if (retryAfterGrant) runOp(op, false, done) else { done(); reloadTick++ } }
                grantLauncher.launch(IntentSenderRequest.Builder(r.sender).build())
            }
            is VideoOps.Result.Error -> Toast.makeText(ctx, r.message, Toast.LENGTH_LONG).show()
        }
    }
    var deleteTarget by remember { mutableStateOf<PlaylistEntity?>(null) }
    var newListDialog by remember { mutableStateOf(false) }
    var crash by remember {
        mutableStateOf(File(ctx.filesDir, "crash.txt").takeIf { it.exists() }?.readText())
    }

    var viewMode by remember { mutableStateOf(prefs.viewMode) }
    var sortKey by remember { mutableStateOf(prefs.sortKey) }
    var ascending by remember { mutableStateOf(prefs.ascending) }
    var query by remember { mutableStateOf("") }
    var videoFilter by remember { mutableStateOf(prefs.videoFilter) }
    // Al cambiar de pestaña, carpeta o filtro se cancela la selección
    LaunchedEffect(tab, folder, open, videoFilter) { selected = emptySet() }
    /** Videos seleccionados, en el mismo orden en que se ven en pantalla. */
    fun selectedVideos(): List<VideoItem> = (visibleVideos + videos).distinctBy { it.id }.filter { it.id in selected }
    var videoSearchOpen by remember { mutableStateOf(false) }
    // Un único desenfoque para toda la pantalla: la barra inferior desenfoca lo que pasa por debajo
    val navHaze = rememberHazeState()
    var bibleBook by remember { mutableIntStateOf(prefs.bibleBook.coerceIn(-1, Bible.books.size - 1)) }
    var bibleChapter by remember {
        mutableIntStateOf(if (bibleBook >= 0) prefs.bibleChapter.coerceIn(0, Bible.books[bibleBook].chapters) else 0)
    }
    var bibleQuery by remember { mutableStateOf("") }
    var bibleRed by remember { mutableStateOf(prefs.bibleRed) }
    LaunchedEffect(bibleBook, bibleChapter) { prefs.bibleBook = bibleBook; prefs.bibleChapter = bibleChapter }
    LaunchedEffect(resumeTick) { bibleRed = prefs.bibleRed }
    val searchFocus = remember { FocusRequester() }
    var menuOpen by remember { mutableStateOf(false) }
    val searchActive = (query.isNotBlank() && open == null && tab == 0) || (bibleQuery.isNotBlank() && tab == 2)

    var hiddenFolders by remember { mutableStateOf(prefs.hiddenFolders) }
    var hiddenThumbs by remember { mutableStateOf(prefs.hiddenThumbs) }
    var showHidden by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf<String?>(null) }
    var themeDialog by remember { mutableStateOf(false) }

    var searchCollapsed by remember { mutableStateOf(false) }
    var focusTick by remember { mutableIntStateOf(0) }
    val searchHidden = searchCollapsed && !searchActive
    LaunchedEffect(tab, folder, open) { searchCollapsed = false }
    LaunchedEffect(focusTick) {
        if (focusTick > 0) {
            delay(200)
            runCatching { searchFocus.requestFocus() }
        }
    }
    val scrollThreshold = with(LocalDensity.current) { 14.dp.toPx() }
    val scrollConnection = remember(scrollThreshold) {
        object : NestedScrollConnection {
            private var acc = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy != 0f) {
                    if ((dy < 0f && acc > 0f) || (dy > 0f && acc < 0f)) acc = 0f
                    acc += dy
                    if (acc < -scrollThreshold) searchCollapsed = true
                    else if (acc > scrollThreshold) searchCollapsed = false
                }
                return Offset.Zero
            }
        }
    }

    fun play(uris: List<String>, titles: List<String>, start: Int) {
        ctx.startActivity(PlayerActivity.intent(ctx, uris.map { Uri.parse(it) }, titles, start))
    }
    fun closeSearch() { if (tab == 2) bibleQuery = "" else { query = ""; videoSearchOpen = false } }
    fun goBack() {
        when {
            selecting -> selected = emptySet()
            searchActive -> closeSearch()
            tab == 0 && videoSearchOpen -> closeSearch()
            open != null -> open = null
            tab == 2 -> { if (bibleChapter > 0) bibleChapter = 0 else bibleBook = -1 }
            else -> folder = null
        }
    }
    BackHandler(selecting || searchActive || open != null || (tab == 0 && (folder != null || videoSearchOpen)) || (tab == 2 && bibleBook >= 0)) { goBack() }

    // Fondo con manchas de color sacadas de las miniaturas: dentro de una carpeta usa los videos de
    // esa carpeta; en el resto de pantallas, las carpetas más grandes. Cambia con una transición suave.
    val artUris = remember(videos, tab, folder) {
        val inFolder = if (tab == 0 && folder != null) videos.filter { it.folder == folder } else emptyList()
        if (inFolder.isNotEmpty()) {
            listOf(0, inFolder.size / 2, inFolder.lastIndex).distinct().map { inFolder[it].uri }
        } else {
            videos.groupBy { it.folder }.entries.sortedByDescending { it.value.size }.take(3).map { it.value.first().uri }
        }
    }
    val backdropArt by produceState<List<Color>>(emptyList(), artUris) {
        value = if (artUris.isEmpty()) emptyList() else withContext(Dispatchers.IO) {
            // El color de cada video se guarda en el dispositivo: la próxima vez sale al instante, sin decodificar la miniatura
            val colorCache = ctx.getSharedPreferences("ucd_backdrop", Context.MODE_PRIVATE)
            val cols = artUris.mapNotNull { u ->
                if (colorCache.contains(u)) Color(colorCache.getInt(u, 0))
                else runCatching {
                    (thumbCache.get(u) ?: loadThumb(ctx, u))?.asAndroidBitmap()
                        ?.let { BackdropArt.colors(listOf(it)).firstOrNull() }
                }.getOrNull()?.also { colorCache.edit().putInt(u, it.toArgb()).apply() }
            }
            // Con menos de 3 colores se repiten, para que las 3 manchas siempre tengan color
            if (cols.isEmpty()) cols else List(3) { cols[it % cols.size] }
        }
    }
    Box(Modifier.fillMaxSize()) {
    GlassBackdrop(art = backdropArt)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        AnimatedContent(
                            targetState = (if (selecting) (if (selected.size == 1) "1 seleccionado" else "${selected.size} seleccionados") else null)
                                ?: open?.name ?: (if (tab == 0) folder else null)
                                ?: (if (tab == 2) {
                                    if (bibleBook < 0) "Biblia"
                                    else Bible.books[bibleBook].name + (if (bibleChapter > 0) " $bibleChapter" else "")
                                } else null)
                                ?: "UCD Player",
                            transitionSpec = {
                                if (Perf.enabled) ContentTransform(EnterTransition.None, ExitTransition.None)
                                else (fadeIn(tween(220, delayMillis = 80)) +
                                    scaleIn(spring(0.7f, Spring.StiffnessMediumLow), initialScale = 0.85f)) togetherWith
                                    fadeOut(tween(100))
                            },
                            label = "title",
                        ) { t ->
                            Text(
                                t,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = LocalUcdStyle.current.titleSize.sp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                    navigationIcon = {
                        val showBack = selecting || searchActive || open != null ||
                            (tab == 0 && (folder != null || videoSearchOpen)) || (tab == 2 && bibleBook >= 0)
                        // En la pantalla principal va el logo; en el resto, el botón de atrás
                        AnimatedContent(
                            targetState = showBack,
                            transitionSpec = {
                                if (Perf.enabled) ContentTransform(EnterTransition.None, ExitTransition.None)
                                else (scaleIn(spring(0.5f, Spring.StiffnessMedium)) + fadeIn()) togetherWith
                                    (scaleOut() + fadeOut())
                            },
                            label = "navIcon",
                        ) { back ->
                            if (back) {
                                GlassIconButton(onClick = { goBack() }) {
                                    if (selecting) Icon(Icons.Rounded.Close, "Cancelar selección")
                                    else if (searchActive || (tab == 0 && videoSearchOpen)) Icon(Icons.Rounded.Close, "Cerrar búsqueda")
                                    else Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás")
                                }
                            } else {
                                AppLogo()
                            }
                        }
                    },
                    actions = {
                        // Con videos seleccionados: seleccionar todos y mover
                        AnimatedVisibility(
                            visible = selecting,
                            enter = scaleIn(spring(0.5f, Spring.StiffnessMedium)) + fadeIn(),
                            exit = scaleOut() + fadeOut(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { selected = visibleVideos.map { it.id }.toSet() }) {
                                    Text("Seleccionar todos", maxLines = 1)
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = open == null && tab == 0 && !videoSearchOpen && !selecting,
                            enter = scaleIn(spring(0.5f, Spring.StiffnessMedium)) + fadeIn(),
                            exit = scaleOut() + fadeOut(),
                        ) {
                            GlassIconButton(onClick = { ctx.startActivity(Intent(ctx, DownloadActivity::class.java)) }) {
                                Icon(Icons.Rounded.Download, "Descargar video")
                            }
                        }
                        AnimatedVisibility(
                            visible = open == null && !selecting && ((tab == 0 && !videoSearchOpen) || (tab == 2 && searchHidden)),
                            enter = scaleIn(spring(0.5f, Spring.StiffnessMedium)) + fadeIn(),
                            exit = scaleOut() + fadeOut(),
                        ) {
                            GlassIconButton(onClick = {
                                if (tab == 0) videoSearchOpen = true else searchCollapsed = false
                                focusTick++
                            }) {
                                Icon(Icons.Rounded.Search, "Buscar")
                            }
                        }
                        AnimatedVisibility(
                            visible = open == null && !selecting,
                            enter = scaleIn(spring(0.5f, Spring.StiffnessMedium)) + fadeIn(),
                            exit = scaleOut() + fadeOut(),
                        ) {
                            Box {
                                GlassIconButton(onClick = { menuOpen = true }) { FourDotsIcon() }
                                GlassDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    if (tab == 0) {
                                    MenuHeader("Vista")
                                    ViewMode.entries.forEach { m ->
                                        CheckItem(m.label, viewMode == m) {
                                            viewMode = m; prefs.viewMode = m; menuOpen = false
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                                    MenuHeader("Ordenar por")
                                    SortKey.entries.forEach { k ->
                                        CheckItem(k.label, sortKey == k) {
                                            sortKey = k; prefs.sortKey = k; menuOpen = false
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                                    CheckItem("Ascendente (A→Z, menor a mayor)", ascending) {
                                        ascending = true; prefs.ascending = true; menuOpen = false
                                    }
                                    CheckItem("Descendente (Z→A, mayor a menor)", !ascending) {
                                        ascending = false; prefs.ascending = false; menuOpen = false
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                                    CheckItem("Mostrar carpetas ocultas", showHidden) {
                                        showHidden = !showHidden; menuOpen = false
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Tema") },
                                        onClick = { menuOpen = false; themeDialog = true },
                                        leadingIcon = { Icon(Icons.Rounded.Palette, null) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Ajustes") },
                                        onClick = { menuOpen = false; ctx.startActivity(Intent(ctx, SettingsActivity::class.java)) },
                                        leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Transmitir pantalla") },
                                        onClick = { menuOpen = false; openCastMenu(ctx) },
                                        leadingIcon = { Icon(Icons.Rounded.Cast, null) },
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = tab == 2,
                            enter = scaleIn(spring(0.5f, Spring.StiffnessMedium)) + fadeIn(),
                            exit = scaleOut() + fadeOut(),
                        ) {
                            GlassIconButton(onClick = { bibleRed = !bibleRed; prefs.bibleRed = bibleRed }) {
                                Icon(
                                    Icons.Rounded.FormatColorText, "Palabras de Cristo en rojo",
                                    tint = if (bibleRed) LocalUcdStyle.current.christRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
                // Biblia: el buscador de siempre
                AnimatedVisibility(
                    visible = open == null && tab == 2 && !searchHidden,
                    enter = expandVertically(spring(0.8f, Spring.StiffnessMediumLow)) + fadeIn() +
                        scaleIn(initialScale = 0.9f),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    SearchPill(bibleQuery, { bibleQuery = it }, searchFocus, "Buscar libro, cita (Jn 3:16) o texto…")
                }
                // Videos: el buscador aparece al tocar la lupa
                AnimatedVisibility(
                    visible = open == null && tab == 0 && videoSearchOpen,
                    enter = expandVertically(spring(0.8f, Spring.StiffnessMediumLow)) + fadeIn() +
                        scaleIn(initialScale = 0.9f),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    SearchPill(query, { query = it }, searchFocus, "Buscar videos…")
                }
                // Videos: chips de filtro (se deslizan hacia los lados)
                AnimatedVisibility(
                    visible = open == null && tab == 0 && !videoSearchOpen && folder == null,
                    enter = expandVertically(spring(0.8f, Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    FilterPills(videoFilter) { videoFilter = it; prefs.videoFilter = it }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = open == null && tab == 1,
                enter = scaleIn(spring(0.45f, Spring.StiffnessMediumLow)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                val fabStyle = LocalUcdStyle.current
                val fabSolid = fabStyle.fabBg != Color.Unspecified
                FloatingActionButton(
                    onClick = { newListDialog = true },
                    modifier = Modifier.padding(bottom = 76.dp).then(if (fabSolid) Modifier else Modifier.glass(CircleShape)),
                    shape = CircleShape,
                    containerColor = if (fabSolid) fabStyle.fabBg else Color.Transparent,
                    contentColor = if (fabSolid) fabStyle.fabFg else MaterialTheme.colorScheme.onBackground,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                ) {
                    Icon(Icons.Rounded.Add, "Nueva lista")
                }
            }
        },
    ) { pad ->
        AnimatedContent(
            targetState = ScreenKey(tab, folder, open, viewMode, videoFilter),
            modifier = Modifier.padding(pad).fillMaxSize().hazeSource(navHaze).nestedScroll(scrollConnection),
            transitionSpec = {
                // Con TV conectado (modo ligero) los cambios de pantalla son instantáneos
                if (Perf.enabled) ContentTransform(EnterTransition.None, ExitTransition.None)
                else popTransition(initialState.rank, targetState.rank)
            },
            label = "screen",
        ) { k ->
            val o = k.open
            when {
                o != null -> PlaylistDetail(dao, o) { u, t, s -> play(u, t, s) }
                k.tab == 0 -> VideosTab(
                    videos, granted, k.folder,
                    query = if (searchActive) query else "",
                    viewMode = k.viewMode, sortKey = sortKey, ascending = ascending,
                    hiddenFolders = hiddenFolders, hiddenThumbs = hiddenThumbs, showHidden = showHidden,
                    chip = k.filter, refresh = resumeTick,
                    onFolderMenu = { folderMenu = it },
                    onOpenFolder = { folder = it },
                    onPlay = { list, i -> play(list.map { it.uri }, list.map { it.title }, i) },
                    onAdd = { videoMenu = it },
                    sel = VideoSelection(
                        ids = selected,
                        onToggle = { v -> selected = if (v.id in selected) selected - v.id else selected + v.id },
                        onVisible = { visibleVideos = it },
                    ),
                    onGrant = { videoLauncher.launch(videoPerms) },
                )
                k.tab == 2 -> BibleTab(
                    book = bibleBook, chapter = bibleChapter, query = bibleQuery, red = bibleRed,
                    onBook = { bibleBook = it; bibleChapter = 0; bibleQuery = "" },
                    onChapter = { bibleChapter = it },
                    onGoTo = { b, c -> bibleBook = b; bibleChapter = c; bibleQuery = "" },
                )
                else -> PlaylistsTab(
                    playlists,
                    onOpen = { open = it },
                    onDelete = { p -> deleteTarget = p },
                )
            }
        }
    }

    AnimatedVisibility(
        visible = open == null && !selecting,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 10.dp),
        enter = if (Perf.enabled) EnterTransition.None
            else slideInVertically(spring(0.65f, Spring.StiffnessMediumLow)) { it } + scaleIn(initialScale = 0.8f) + fadeIn(),
        exit = if (Perf.enabled) ExitTransition.None else slideOutVertically(tween(200)) { it } + fadeOut(),
    ) {
        FloatingNav(tab = tab, onSelect = { tab = it }, haze = navHaze)
    }
    // Con videos seleccionados, esta barra ocupa el lugar de la de abajo: mover, añadir a lista y eliminar
    AnimatedVisibility(
        visible = selecting,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 10.dp),
        enter = if (Perf.enabled) EnterTransition.None
            else slideInVertically(spring(0.65f, Spring.StiffnessMediumLow)) { it } + scaleIn(initialScale = 0.8f) + fadeIn(),
        exit = if (Perf.enabled) ExitTransition.None else slideOutVertically(tween(200)) { it } + fadeOut(),
    ) {
        FloatingSelectionBar(
            haze = navHaze,
            onMove = { moveTargets = selectedVideos() },
            onAddToList = { addTargets = selectedVideos() },
            onDelete = { deleteVideoTargets = selectedVideos() },
        )
    }
    }

    if (newListDialog) {
        var name by remember { mutableStateOf("") }
        GlassAlertDialog(
            onDismissRequest = { newListDialog = false },
            title = { Text("Nueva lista") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Nombre") },
                    shape = rs(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    ))
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) scope.launch { dao.insertPlaylist(PlaylistEntity(name = name.trim())) }
                    newListDialog = false
                }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { newListDialog = false }) { Text("Cancelar") } },
        )
    }

    deleteTarget?.let { p ->
        GlassAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar lista") },
            text = { Text("¿Eliminar \"${p.name}\"? Los videos no se borran del dispositivo.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { dao.deletePlaylist(p.id) }
                    deleteTarget = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } },
        )
    }

    addTargets?.let { vs ->
        GlassAlertDialog(
            onDismissRequest = { addTargets = null },
            title = { Text(if (vs.size > 1) "Añadir ${vs.size} videos a lista" else "Añadir a lista") },
            text = {
                if (playlists.isEmpty()) {
                    Text("Primero crea una lista en la pestaña Listas.")
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        playlists.forEach { p ->
                            GlassMenuItem(p.name) {
                                val toAdd = vs
                                scope.launch { toAdd.forEach { dao.addVideo(p.id, it.uri, it.title) } }
                                if (toAdd.size > 1) {
                                    Toast.makeText(ctx, "${toAdd.size} videos añadidos a \"${p.name}\"", Toast.LENGTH_SHORT).show()
                                }
                                selected = emptySet()
                                addTargets = null
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { addTargets = null }) { Text("Cerrar") } },
        )
    }

    videoMenu?.let { v ->
        GlassAlertDialog(
            onDismissRequest = { videoMenu = null },
            title = {
                Text(
                    v.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassMenuItem("Añadir a lista", Icons.Rounded.Add) { videoMenu = null; addTargets = listOf(v) }
                    GlassMenuItem("Renombrar", Icons.Rounded.Edit) { videoMenu = null; renameTarget = v }
                    GlassMenuItem("Mover a otra carpeta", Icons.Rounded.FolderOpen) { videoMenu = null; moveTargets = listOf(v) }
                    GlassMenuItem("Compartir", Icons.Rounded.Share) {
                        videoMenu = null
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "video/*"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(v.uri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { ctx.startActivity(Intent.createChooser(send, "Compartir video")) }
                    }
                    GlassMenuItem("Eliminar", Icons.Rounded.Delete) { videoMenu = null; deleteVideoTargets = listOf(v) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    TextButton(onClick = { videoMenu = null }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                }
            },
            confirmButton = {},
        )
    }

    renameTarget?.let { v ->
        var name by remember(v.id) { mutableStateOf(v.title) }
        GlassAlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renombrar video") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Nombre") },
                    shape = rs(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val target = v
                        renameTarget = null
                        runOp({ VideoOps.rename(ctx, Uri.parse(target.uri), name) })
                    },
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancelar") } },
        )
    }

    moveTargets?.let { vs ->
        val dests = remember(videos, vs) {
            videos.map { it.relativePath }
                .filter { p -> p.isNotBlank() && VideoOps.ROOTS.any { p.startsWith(it) } && !vs.all { it.relativePath == p } }
                .distinct().sorted()
        }
        var newFolder by remember(vs) { mutableStateOf("") }
        fun moveTo(path: String) {
            val targets = vs
            moveTargets = null
            runOp(
                { VideoOps.moveAll(ctx, targets.map { Uri.parse(it.uri) }, path) },
                done = {
                    selected = emptySet()
                    if (targets.size > 1) Toast.makeText(ctx, "${targets.size} videos movidos", Toast.LENGTH_SHORT).show()
                },
            )
        }
        GlassAlertDialog(
            onDismissRequest = { moveTargets = null },
            title = { Text(if (vs.size > 1) "Mover ${vs.size} videos a otra carpeta" else "Mover a otra carpeta") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (dests.isEmpty()) Text("No hay otras carpetas. Crea una nueva abajo.")
                        dests.forEach { p -> GlassMenuItem(p.trimEnd('/'), Icons.Rounded.Folder) { moveTo(p) } }
                    }
                    OutlinedTextField(
                        value = newFolder, onValueChange = { newFolder = it }, singleLine = true,
                        label = { Text("Nueva carpeta en Movies") },
                        shape = rs(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newFolder.isNotBlank(),
                    onClick = { moveTo("Movies/" + newFolder.replace(Regex("[\\\\/:*?\"<>|]"), "").trim()) },
                ) { Text("Crear y mover") }
            },
            dismissButton = { TextButton(onClick = { moveTargets = null }) { Text("Cancelar") } },
        )
    }

    deleteVideoTargets?.let { vs ->
        GlassAlertDialog(
            onDismissRequest = { deleteVideoTargets = null },
            title = { Text(if (vs.size > 1) "Eliminar ${vs.size} videos" else "Eliminar video") },
            text = {
                Text(
                    if (vs.size > 1) "¿Eliminar ${vs.size} videos del dispositivo? Esta acción no se puede deshacer."
                    else "¿Eliminar \"${vs[0].title}\" del dispositivo? Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val targets = vs
                    deleteVideoTargets = null
                    runOp(
                        { VideoOps.delete(ctx, targets.map { Uri.parse(it.uri) }) },
                        retryAfterGrant = false,
                        done = {
                            scope.launch { dao.removeVideos(targets.map { it.uri }) }
                            selected = emptySet()
                            if (targets.size > 1) Toast.makeText(ctx, "${targets.size} videos eliminados", Toast.LENGTH_SHORT).show()
                        },
                    )
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleteVideoTargets = null }) { Text("Cancelar") } },
        )
    }

    if (themeDialog) {
        ThemePickerDialog(
            currentId = themeId,
            onPick = { prefs.themeId = it; onTheme(it) },
            onDismiss = { themeDialog = false },
        )
    }

    folderMenu?.let { name ->
        val thumbHidden = name in hiddenThumbs
        val folderHidden = name in hiddenFolders
        GlassAlertDialog(
            onDismissRequest = { folderMenu = null },
            title = {
                Text(
                    name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassMenuItem(
                        if (thumbHidden) "Mostrar miniatura" else "Ocultar miniatura",
                        Icons.Rounded.Image,
                    ) {
                        val set = hiddenThumbs.toMutableSet()
                        if (!set.add(name)) set.remove(name)
                        hiddenThumbs = set; prefs.hiddenThumbs = set
                        folderMenu = null
                    }
                    GlassMenuItem(
                        if (folderHidden) "Mostrar carpeta" else "Ocultar carpeta",
                        Icons.Rounded.Folder,
                    ) {
                        val set = hiddenFolders.toMutableSet()
                        if (!set.add(name)) set.remove(name)
                        hiddenFolders = set; prefs.hiddenFolders = set
                        folderMenu = null
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    TextButton(onClick = { folderMenu = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancelar")
                    }
                }
            },
            confirmButton = {},
        )
    }

    crash?.let { text ->
        GlassAlertDialog(
            onDismissRequest = {},
            title = { Text("La app se cerró por un error") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(text.take(2500), style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("error", text.take(2500)))
                }) { Text("Copiar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    File(ctx.filesDir, "crash.txt").delete()
                    crash = null
                }) { Text("Cerrar") }
            },
        )
    }
}

@Composable
private fun SearchPill(query: String, onChange: (String) -> Unit, focus: FocusRequester, hint: String = "Buscar videos…") {
    // Cápsula de cristal fina: solo el icono, el texto y un borde de luz (sin círculos dentro)
    val shape: Shape = if (LocalUcdStyle.current.flat) rs(26.dp) else CircleShape
    var focused by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(
        if (focused) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "searchFocus",
    )
    Surface(
        shape = shape,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(48.dp)
            .graphicsLayer { val sc = 1f + 0.02f * grow; scaleX = sc; scaleY = sc }
            .glass(shape)
            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f * grow), shape),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Search, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 18.dp).size(20.dp),
            )
            BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp).focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(
                            hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Close, "Borrar", Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FloatingNav(tab: Int, onSelect: (Int) -> Unit, haze: HazeState, modifier: Modifier = Modifier) {
    val st = LocalUcdStyle.current
    // Cápsula oscura y translúcida con botones redondos: el activo es blanco y muestra su nombre
    val shape: Shape = if (st.flat) rs(28.dp) else CircleShape
    Row(
        modifier.glassCapsule(shape, haze).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(tab == 0, Icons.Rounded.VideoLibrary, "Videos", shape) { onSelect(0) }
        NavItem(tab == 1, Icons.Rounded.Folder, "Listas", shape) { onSelect(1) }
        NavItem(tab == 2, Icons.Rounded.AutoStories, "Biblia", shape) { onSelect(2) }
    }
}

/** Barra de acciones para los videos seleccionados (ocupa el lugar de la barra de pestañas). */
@Composable
private fun FloatingSelectionBar(
    haze: HazeState, onMove: () -> Unit, onAddToList: () -> Unit, onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val st = LocalUcdStyle.current
    val shape: Shape = if (st.flat) rs(28.dp) else CircleShape
    Row(
        modifier.glassCapsule(shape, haze).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionAction(Icons.Rounded.FolderOpen, "Mover", shape, onMove)
        SelectionAction(Icons.Rounded.Add, "A lista", shape, onAddToList)
        SelectionAction(Icons.Rounded.Delete, "Eliminar", shape, onDelete)
    }
}

@Composable
private fun SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, shape: Shape, onClick: () -> Unit,
) {
    val st = LocalUcdStyle.current
    val glassy = !st.flat
    val bg = if (glassy) Color.White.copy(alpha = 0.12f) else Color.Transparent
    val fg = if (glassy) (if (st.navMuted != Color.Unspecified) st.navMuted else Color.White.copy(alpha = 0.85f)) else st.muted
    Row(
        Modifier.height(46.dp).clip(shape).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun NavItem(
    selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
    shape: Shape, onClick: () -> Unit,
) {
    val st = LocalUcdStyle.current
    val glassy = !st.flat
    val selBg = if (glassy) Color.White else st.selFill
    val selFg = if (glassy) (if (st.fabFg != Color.Unspecified) st.fabFg else Color(0xFF1B2030)) else st.selText
    val idleBg = if (glassy) Color.White.copy(alpha = 0.12f) else Color.Transparent
    val idleFg = if (glassy) (if (st.navMuted != Color.Unspecified) st.navMuted else Color.White.copy(alpha = 0.85f)) else st.muted
    val bg by animateColorAsState(
        if (selected) selBg else idleBg,
        if (Perf.enabled) snap<Color>() else spring<Color>(stiffness = Spring.StiffnessMediumLow), label = "navBg",
    )
    val fg by animateColorAsState(
        if (selected) selFg else idleFg,
        if (Perf.enabled) snap<Color>() else spring<Color>(stiffness = Spring.StiffnessMediumLow), label = "navFg",
    )
    // Efecto "gota de agua" al seleccionar: la pastilla cae y rebota (squash & stretch) y
    // una onda circular sale desde el icono. Funciona en todos los temas, incluidos los planos.
    val started = remember { mutableStateOf(false) }
    val drop = remember { Animatable(1f) }      // rebote de la pastilla (1 = reposo)
    val ripple = remember { Animatable(1f) }    // onda: 0 = empieza, 1 = terminó
    LaunchedEffect(selected) {
        if (!started.value) { started.value = true; return@LaunchedEffect }
        // Con el modo ligero activo (TV conectado) no se anima, igual que el resto de la app
        if (selected && !Perf.enabled) {
            launch { drop.snapTo(0.72f); drop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 380f)) }
            launch { ripple.snapTo(0f); ripple.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
        }
    }
    val rippleColor = if (glassy) Color.White else st.selFill
    Row(
        Modifier
            .graphicsLayer {
                // Se aplasta al caer y se estira al rebotar: la forma de una gota
                val s = drop.value
                scaleX = 1f + (1f - s) * 0.35f
                scaleY = s
            }
            .height(46.dp)
            .clip(shape)
            .background(bg)
            .drawWithContent {
                drawContent()
                val p = ripple.value
                if (p < 1f) {
                    val maxR = size.height * 1.1f
                    val r = size.height * 0.45f + (maxR - size.height * 0.45f) * p
                    drawCircle(
                        color = rippleColor.copy(alpha = (1f - p) * 0.55f),
                        radius = r,
                        center = Offset(size.height / 2f, size.height / 2f),
                        style = Stroke(width = 2.dp.toPx() * (1f - p) + 0.5f),
                    )
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(22.dp))
        AnimatedVisibility(
            visible = selected,
            enter = if (Perf.enabled) EnterTransition.None
                else expandHorizontally(spring(0.8f, Spring.StiffnessMediumLow)) + fadeIn(),
            exit = if (Perf.enabled) ExitTransition.None
                else shrinkHorizontally(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) {
            Text(
                label, color = fg, style = MaterialTheme.typography.labelLarge,
                maxLines = 1, softWrap = false,
                modifier = Modifier.padding(start = 8.dp, end = 6.dp),
            )
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CheckItem(text: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = {
            if (checked) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp))
        },
    )
}

@Composable
private fun gridColumns(compact: Boolean): Int {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    return if (landscape) (if (compact) 6 else 4) else (if (compact) 3 else 2)
}

/**
 * Logo de la app: solo el asterisco de ocho rayos (del color del tema, blanco en los temas oscuros)
 * con el play de degradado lavanda y azul al centro. Se dibuja una sola vez con drawWithCache.
 */
@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onBackground
    Box(
        modifier
            .padding(start = 12.dp, end = 8.dp)
            .size(32.dp)
            .drawWithCache {
                val s = size.minDimension
                val armLen = s * 0.94f
                val armW = s * 0.16f
                val armR = CornerRadius(armW * 0.28f)
                val tri = roundedTriangle(
                    Offset(s * 0.370f, s * 0.299f), Offset(s * 0.370f, s * 0.701f), Offset(s * 0.719f, s * 0.5f),
                    s * 0.062f,
                )
                val triBrush = Brush.linearGradient(
                    listOf(Color(0xFFC39EEA), Color(0xFFA9C8F0)),
                    start = Offset(s * 0.36f, s * 0.70f), end = Offset(s * 0.72f, s * 0.30f),
                )
                onDrawBehind {
                    val c = Offset(s / 2f, s / 2f)
                    for (k in 0 until 4) rotate(45f * k, c) {
                        drawRoundRect(
                            color, Offset(c.x - armW / 2f, c.y - armLen / 2f),
                            GSize(armW, armLen), armR,
                        )
                    }
                    drawPath(tri, triBrush)
                }
            },
    )
}

/** Triángulo con las tres esquinas redondeadas (para el play del logo). */
private fun roundedTriangle(a: Offset, b: Offset, c: Offset, r: Float): Path {
    fun toward(from: Offset, to: Offset): Offset {
        val d = to - from
        val len = sqrt(d.x * d.x + d.y * d.y)
        return from + d * (r / len)
    }
    val pts = listOf(a, b, c)
    val path = Path()
    for (i in pts.indices) {
        val prev = pts[(i + 2) % 3]
        val cur = pts[i]
        val next = pts[(i + 1) % 3]
        val p1 = toward(cur, prev)
        val p2 = toward(cur, next)
        if (i == 0) path.moveTo(p1.x, p1.y) else path.lineTo(p1.x, p1.y)
        path.quadraticTo(cur.x, cur.y, p2.x, p2.y)
    }
    path.close()
    return path
}

/** Icono de cuatro puntos (menú) como el de las referencias. */
@Composable
private fun FourDotsIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(22.dp)) {
        val m = size.minDimension
        val o = m * 0.22f
        val c = center
        for (dx in listOf(-o, o)) for (dy in listOf(-o, o)) {
            drawCircle(color, m * 0.10f, Offset(c.x + dx, c.y + dy))
        }
    }
}

/** Fila de chips bajo el título: se desliza hacia los lados. */
@Composable
private fun FilterPills(selected: VideoFilter, onSelect: (VideoFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoFilter.entries.forEach { f -> FilterPill(f.label, f == selected) { onSelect(f) } }
    }
}

@Composable
internal fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val st = LocalUcdStyle.current
    // Seleccionado: amarillo pastel sólido (en los temas planos, el color de selección del tema)
    val selBg = if (st.flat) st.selFill else Color(0xFFFFF48A)
    val selFg = if (st.flat) st.selText else Color(0xFF1B2030)
    val bg by animateColorAsState(
        if (selected) selBg else Color.Transparent, spring(stiffness = Spring.StiffnessMediumLow), label = "pillBg",
    )
    val fg by animateColorAsState(
        if (selected) selFg else MaterialTheme.colorScheme.onSurface,
        spring(stiffness = Spring.StiffnessMediumLow), label = "pillFg",
    )
    Box(
        Modifier
            .height(40.dp)
            .bounceClick(CircleShape, onClick)
            .glass(CircleShape)
            .background(bg)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
    }
}

private fun pad(h: Dp) = PaddingValues(start = h, top = 8.dp, end = h, bottom = 104.dp)

private data class ScreenKey(
    val tab: Int, val folder: String?, val open: PlaylistEntity?, val viewMode: ViewMode, val filter: VideoFilter,
) {
    val rank: Int get() = when {
        open != null -> 20
        tab == 1 || tab == 2 -> 10
        folder != null -> 5
        else -> 0
    }
}

internal fun popTransition(from: Int, to: Int): ContentTransform {
    val pop = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    return when {
        to > from ->
            (fadeIn(tween(200, delayMillis = 60)) + scaleIn(pop, initialScale = 0.86f)) togetherWith
                (fadeOut(tween(110)) + scaleOut(tween(220), targetScale = 1.06f))
        to < from ->
            (fadeIn(tween(200, delayMillis = 60)) + scaleIn(pop, initialScale = 1.08f)) togetherWith
                (fadeOut(tween(110)) + scaleOut(tween(220), targetScale = 0.9f))
        else ->
            (fadeIn(tween(180, delayMillis = 40)) + scaleIn(pop, initialScale = 0.94f)) togetherWith
                fadeOut(tween(100))
    }
}

@Composable
internal fun Modifier.bounceClick(shape: Shape, onClick: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(
        if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "press",
    )
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                do {
                    val e = awaitPointerEvent()
                } while (e.changes.any { it.pressed && !it.isConsumed })
                pressed = false
            }
        }
        .clip(shape)
        .clickable(onClick = onClick)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.bounceClickLong(shape: Shape, onLongClick: () -> Unit, onClick: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val s by animateFloatAsState(
        if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "pressLong",
    )
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                do {
                    val e = awaitPointerEvent()
                } while (e.changes.any { it.pressed && !it.isConsumed })
                pressed = false
            }
        }
        .clip(shape)
        .combinedClickable(
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            },
            onClick = onClick,
        )
}

@Composable
private fun Modifier.popIn(key: Any, index: Int, seen: MutableSet<Any>): Modifier {
    if (Perf.enabled) return this   // modo ligero (TV conectado): sin animación de entrada
    val play = remember { index < 14 && seen.add(key) }
    if (!play) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 30L)
        p.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
    }
    return this.graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        val sc = 0.78f + 0.22f * v
        scaleX = sc; scaleY = sc
    }
}

private val chunkRegex = Regex("\\d+|\\D+")

private fun naturalCompare(a: String, b: String): Int {
    val x = chunkRegex.findAll(a.lowercase()).map { it.value }.toList()
    val y = chunkRegex.findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(x.size, y.size)) {
        val p = x[i]; val q = y[i]
        val c = if (p[0].isDigit() && q[0].isDigit()) {
            val pp = p.trimStart('0'); val qq = q.trimStart('0')
            val l = pp.length.compareTo(qq.length)
            if (l != 0) l else pp.compareTo(qq)
        } else p.compareTo(q)
        if (c != 0) return c
    }
    return x.size.compareTo(y.size)
}

private fun sortVideos(list: List<VideoItem>, key: SortKey, asc: Boolean): List<VideoItem> {
    val cmp: Comparator<VideoItem> = when (key) {
        SortKey.Name -> Comparator { a, b -> naturalCompare(a.title, b.title) }
        SortKey.Date -> compareBy<VideoItem> { it.dateAdded }
        SortKey.Duration -> compareBy<VideoItem> { it.durationMs }
        SortKey.Size -> compareBy<VideoItem> { it.sizeBytes }
    }
    return list.sortedWith(if (asc) cmp else cmp.reversed())
}

private data class FolderGroup(
    val name: String, val count: Int, val coverUri: String,
    val newest: Long, val durationMs: Long, val sizeBytes: Long,
)

private fun buildGroups(videos: List<VideoItem>, key: SortKey, asc: Boolean): List<FolderGroup> {
    val groups = videos.groupBy { it.folder }.map { (name, list) ->
        FolderGroup(
            name, list.size, list.maxByOrNull { it.dateAdded }!!.uri,
            list.maxOf { it.dateAdded }, list.sumOf { it.durationMs }, list.sumOf { it.sizeBytes },
        )
    }
    val cmp: Comparator<FolderGroup> = when (key) {
        SortKey.Name -> Comparator { a, b -> naturalCompare(a.name, b.name) }
        SortKey.Date -> compareBy<FolderGroup> { it.newest }
        SortKey.Duration -> compareBy<FolderGroup> { it.durationMs }
        SortKey.Size -> compareBy<FolderGroup> { it.sizeBytes }
    }
    return groups.sortedWith(if (asc) cmp else cmp.reversed())
}

private val thumbCache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 1024 / 6).toInt()) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4 / 1024
}

private val thumbGate = Semaphore(3)

// Miniaturas guardadas en disco (en la caché de la app): al abrir la app o volver a una carpeta se leen
// de un archivo pequeño en vez de pedírselas al sistema otra vez. Android puede limpiarlas si falta espacio.
private const val THUMB_CACHE_MAX = 150L * 1024 * 1024
private const val THUMB_CACHE_KEEP = 110L * 1024 * 1024
private var thumbWrites = 0

private fun thumbDiskFile(ctx: Context, uri: String): File =
    File(File(ctx.cacheDir, "thumbs"), uri.replace(Regex("[^A-Za-z0-9]"), "_") + ".jpg")

/** Si la carpeta pasa del límite, borra las miniaturas más viejas. */
private fun trimThumbDisk(dir: File) {
    val files = dir.listFiles() ?: return
    var total = files.sumOf { it.length() }
    if (total <= THUMB_CACHE_MAX) return
    for (f in files.sortedBy { it.lastModified() }) {
        total -= f.length()
        f.delete()
        if (total <= THUMB_CACHE_KEEP) break
    }
}

@Suppress("DEPRECATION")
private fun loadThumbFromSystem(ctx: Context, uri: String): android.graphics.Bitmap? = runCatching {
    val u = Uri.parse(uri)
    if (Build.VERSION.SDK_INT >= 29) {
        ctx.contentResolver.loadThumbnail(u, Size(400, 400), null)
    } else {
        MediaStore.Video.Thumbnails.getThumbnail(
            ctx.contentResolver, ContentUris.parseId(u), MediaStore.Video.Thumbnails.MINI_KIND, null,
        )
    }
}.getOrNull()

private fun loadThumb(ctx: Context, uri: String): ImageBitmap? {
    val file = thumbDiskFile(ctx, uri)
    if (file.exists()) {
        val fromDisk = runCatching { android.graphics.BitmapFactory.decodeFile(file.path) }.getOrNull()
        if (fromDisk != null) return fromDisk.asImageBitmap()
        file.delete()   // archivo dañado: se vuelve a pedir al sistema
    }
    val bitmap = loadThumbFromSystem(ctx, uri) ?: return null
    runCatching {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
        if (!tmp.renameTo(file)) tmp.delete()
        if (++thumbWrites % 50 == 0) file.parentFile?.let { trimThumbDisk(it) }
    }
    return bitmap.asImageBitmap()
}

/** Prepara en segundo plano las miniaturas que aún no están en memoria (una a una, sin apurar al sistema). */
internal suspend fun prefetchThumbs(ctx: Context, uris: List<String>) {
    for (u in uris) {
        if (thumbCache.get(u) != null) continue
        val loaded = thumbGate.withPermit { withContext(Dispatchers.IO) { loadThumb(ctx, u) } }
        if (loaded != null) thumbCache.put(u, loaded)
        delay(120)
    }
}

@Composable
internal fun VideoThumb(uri: String, modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = thumbCache.get(uri), uri) {
        if (value == null) {
            delay(80)
            val loaded = thumbGate.withPermit { withContext(Dispatchers.IO) { loadThumb(ctx, uri) } }
            if (loaded != null) {
                thumbCache.put(uri, loaded)
                value = loaded
            }
        }
    }
    Box(
        modifier
            .clip(rs(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        val a by animateFloatAsState(if (b != null) 1f else 0f, tween(250), label = "thumb")
        if (b != null) {
            Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = a })
        } else {
            Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CenterMessage(text: String, content: @Composable () -> Unit = {}) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        enter = scaleIn(spring(0.55f, Spring.StiffnessMediumLow), initialScale = 0.7f) + fadeIn(),
    ) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text, textAlign = TextAlign.Center)
            content()
        }
    }
}

@Composable
private fun VideosTab(
    videos: List<VideoItem>, granted: Boolean, folder: String?,
    query: String, viewMode: ViewMode, sortKey: SortKey, ascending: Boolean,
    hiddenFolders: Set<String>, hiddenThumbs: Set<String>, showHidden: Boolean,
    chip: VideoFilter, refresh: Int,
    onFolderMenu: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onPlay: (List<VideoItem>, Int) -> Unit,
    onAdd: (VideoItem) -> Unit,
    sel: VideoSelection,
    onGrant: () -> Unit,
) {
    if (!granted) {
        CenterMessage("Necesito acceso a los videos de tu dispositivo") {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onGrant) { Text("Dar permiso") }
        }
        return
    }
    if (videos.isEmpty()) { CenterMessage("No se encontraron videos"); return }

    val shown = remember(videos, hiddenFolders, showHidden) {
        if (showHidden) videos else videos.filter { it.folder !in hiddenFolders }
    }
    if (shown.isEmpty()) {
        CenterMessage("Todas las carpetas están ocultas.\nActívalas en el menú ⋮ → Mostrar carpetas ocultas")
        return
    }

    val q = query.trim()
    if (q.isNotEmpty()) {
        val list = remember(shown, q, sortKey, ascending) {
            sortVideos(shown.filter { it.title.contains(q, ignoreCase = true) }, sortKey, ascending)
        }
        if (list.isEmpty()) CenterMessage("Sin resultados para \"$q\"")
        else VideoCollection(list, viewMode, onPlay, onAdd, sel)
    } else if (folder == null && chip != VideoFilter.Folders) {
        val ctx = LocalContext.current
        val prefs = remember { UcdPrefs(ctx) }
        val list = remember(shown, chip, sortKey, ascending, refresh) {
            when (chip) {
                VideoFilter.Recent -> shown.sortedByDescending { it.dateAdded }.take(60)
                VideoFilter.Started -> sortVideos(shown.filter { prefs.position(it.uri) > 0L }, sortKey, ascending)
                else -> sortVideos(shown, sortKey, ascending)
            }
        }
        if (list.isEmpty()) CenterMessage("Aún no has empezado ningún video")
        else VideoCollection(list, viewMode, onPlay, onAdd, sel)
    } else if (folder == null) {
        val groups = remember(shown, sortKey, ascending) { buildGroups(shown, sortKey, ascending) }
        FolderCollection(groups, viewMode, hiddenFolders, hiddenThumbs, onOpenFolder, onFolderMenu)
    } else {
        val list = remember(shown, folder, sortKey, ascending) {
            sortVideos(shown.filter { it.folder == folder }, sortKey, ascending)
        }
        VideoCollection(list, viewMode, onPlay, onAdd, sel)
    }
}

@Composable
private fun FolderCollection(
    groups: List<FolderGroup>, mode: ViewMode,
    hiddenFolders: Set<String>, hiddenThumbs: Set<String>,
    onOpen: (String) -> Unit, onMenu: (String) -> Unit,
) {
    val seen = remember { mutableSetOf<Any>() }
    val thumbCtx = LocalContext.current
    LaunchedEffect(groups) {
        if (UcdPrefs(thumbCtx).preload && !Perf.enabled) { delay(600); prefetchThumbs(thumbCtx, groups.map { it.coverUri }) }
    }
    if (mode == ViewMode.Rows) {
        LazyColumn(
            contentPadding = pad(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(groups, key = { _, g -> g.name }) { i, g ->
                ListItem(
                    modifier = (if (Perf.enabled) Modifier else Modifier.animateItem()).popIn(g.name, i, seen)
                        .bounceClickLong(MaterialTheme.shapes.large, { onMenu(g.name) }) { onOpen(g.name) }
                        .glass(MaterialTheme.shapes.large),
                    leadingContent = {
                        if (g.name in hiddenThumbs) {
                            Box(
                                Modifier.size(96.dp, 54.dp).glass(rs(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else VideoThumb(g.coverUri, Modifier.size(96.dp, 54.dp))
                    },
                    headlineContent = { Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            "${g.count} videos · ${fmtSize(g.sizeBytes)}" + if (g.name in hiddenFolders) " · Oculta" else "",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }
    } else {
        val compact = mode == ViewMode.Compact
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns(compact)),
            contentPadding = pad(if (compact) 12.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            gridItemsIndexed(groups, key = { _, g -> g.name }) { i, g ->
                FolderTile(
                    g.name, g.count, g.coverUri, compact,
                    modifier = (if (Perf.enabled) Modifier else Modifier.animateItem()).popIn(g.name, i, seen),
                    thumbHidden = g.name in hiddenThumbs,
                    hidden = g.name in hiddenFolders,
                    onLongClick = { onMenu(g.name) },
                ) { onOpen(g.name) }
            }
        }
    }
}

/** Videos seleccionados (se activa manteniendo pulsado un video). */
private class VideoSelection(
    val ids: Set<Long>,
    val onToggle: (VideoItem) -> Unit,
    /** Avisa de qué videos se ven ahora, para "Seleccionar todos". */
    val onVisible: (List<VideoItem>) -> Unit,
) {
    val active: Boolean get() = ids.isNotEmpty()
}

@Composable
private fun VideoCollection(
    list: List<VideoItem>, mode: ViewMode,
    onPlay: (List<VideoItem>, Int) -> Unit, onAdd: (VideoItem) -> Unit,
    sel: VideoSelection,
) {
    val seen = remember { mutableSetOf<Any>() }
    val thumbCtx = LocalContext.current
    LaunchedEffect(list) { sel.onVisible(list) }
    LaunchedEffect(list) {
        if (UcdPrefs(thumbCtx).preload && !Perf.enabled) { delay(600); prefetchThumbs(thumbCtx, list.map { it.uri }) }
    }
    if (mode == ViewMode.Rows) {
        LazyColumn(
            contentPadding = pad(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(list, key = { _, v -> v.id }) { i, v ->
                val picked = v.id in sel.ids
                ListItem(
                    modifier = (if (Perf.enabled) Modifier else Modifier.animateItem()).popIn(v.id, i, seen)
                        .bounceClickLong(
                            MaterialTheme.shapes.large,
                            onLongClick = { sel.onToggle(v) },
                            onClick = { if (sel.active) sel.onToggle(v) else onPlay(list, i) },
                        )
                        .glass(MaterialTheme.shapes.large)
                        .then(
                            if (picked) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                            else Modifier
                        ),
                    leadingContent = { VideoThumb(v.uri, Modifier.size(96.dp, 54.dp)) },
                    headlineContent = { Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            fmt(v.durationMs) + " · " + fmtSize(v.sizeBytes) + " · " + fmtDate(v.dateAdded),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    trailingContent = {
                        if (sel.active) {
                            Icon(
                                if (picked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                if (picked) "Seleccionado" else "No seleccionado",
                                tint = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            IconButton(onClick = { onAdd(v) }) { Icon(Icons.Rounded.MoreVert, "Opciones del video") }
                        }
                    },
                )
            }
        }
    } else {
        val compact = mode == ViewMode.Compact
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns(compact)),
            contentPadding = pad(if (compact) 8.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        ) {
            gridItemsIndexed(list, key = { _, v -> v.id }) { i, v ->
                VideoTile(
                    v, compact,
                    modifier = (if (Perf.enabled) Modifier else Modifier.animateItem()).popIn(v.id, i, seen),
                    picked = v.id in sel.ids, selecting = sel.active,
                    onLongClick = { sel.onToggle(v) },
                    onClick = { if (sel.active) sel.onToggle(v) else onPlay(list, i) },
                    onAdd = { onAdd(v) },
                )
            }
        }
    }
}

@Composable
private fun VideoTile(
    v: VideoItem, compact: Boolean, modifier: Modifier = Modifier,
    picked: Boolean, selecting: Boolean, onLongClick: () -> Unit,
    onClick: () -> Unit, onAdd: () -> Unit,
) {
    Column(modifier.bounceClickLong(rs(10.dp), onLongClick, onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .glassBorder(rs(if (compact) 12.dp else 16.dp)),
        ) {
            VideoThumb(v.uri, Modifier.fillMaxSize(), corner = if (compact) 12.dp else 16.dp)
            if (picked) {
                Box(
                    Modifier.fillMaxSize().clip(rs(if (compact) 12.dp else 16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                )
            }
            if (selecting) {
                Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp), contentAlignment = Alignment.Center) {
                    if (picked) Box(Modifier.size(14.dp).background(Color.White, CircleShape))
                    Icon(
                        if (picked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        if (picked) "Seleccionado" else "No seleccionado",
                        Modifier.size(24.dp),
                        tint = if (picked) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
            }
            // Duración: cápsula de cristal sobre la miniatura
            Text(
                fmt(v.durationMs), color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .glassBadge(CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                v.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
            )
            if (selecting) Spacer(Modifier.size(32.dp))
            else IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.MoreVert, "Opciones del video", Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FolderTile(
    name: String, count: Int, coverUri: String, compact: Boolean,
    modifier: Modifier = Modifier,
    thumbHidden: Boolean = false, hidden: Boolean = false, onLongClick: () -> Unit = {},
    onClick: () -> Unit,
) {
    val shape = rs(if (compact) 24.dp else 32.dp)
    val st = LocalUcdStyle.current
    // Sin miniatura, los temas pastel pintan la carpeta con un color suave (siempre el mismo por nombre)
    val pastel = if (thumbHidden && st.pastels.isNotEmpty()) {
        st.pastels[(name.hashCode() and 0x7fffffff) % st.pastels.size]
    } else null
    val fg = when {
        pastel != null -> st.pastelText
        thumbHidden -> MaterialTheme.colorScheme.onSurface
        else -> Color.White
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer { alpha = if (hidden) 0.6f else 1f }
            .bounceClickLong(shape, onLongClick, onClick)
            .then(
                if (pastel != null) Modifier.clip(shape).background(pastel).glassBorder(shape)
                else if (thumbHidden) Modifier.glass(shape)
                else Modifier.glassBorder(shape)
            )
    ) {
        if (thumbHidden) {
            Icon(
                Icons.Rounded.Folder, null,
                Modifier.align(Alignment.Center).size(if (compact) 34.dp else 52.dp),
                tint = if (pastel != null) st.pastelText.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            VideoThumb(coverUri, Modifier.fillMaxSize(), corner = 0.dp)
            // En los temas de cristal la miniatura se ve limpia y el nombre va en una etiqueta
            // de cristal desenfocado; en los planos se mantiene el degradado oscuro de siempre.
            if (st.flat) Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))
                )
            )
        }
        if (hidden) Icon(
            Icons.Rounded.VisibilityOff, "Carpeta oculta",
            Modifier.align(Alignment.TopEnd).padding(10.dp).size(18.dp),
            tint = fg.copy(alpha = 0.85f),
        )
        val labelOnGlass = !thumbHidden && !st.flat
        // Cantidad de videos: cápsula pequeña de cristal arriba a la izquierda
        if (labelOnGlass) Row(
            Modifier.align(Alignment.TopStart).padding(if (compact) 8.dp else 12.dp)
                .glassBadge(CircleShape)
                .padding(horizontal = if (compact) 6.dp else 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.VideoLibrary, null, Modifier.size(if (compact) 11.dp else 13.dp), tint = Color.White)
            Spacer(Modifier.width(4.dp))
            Text("$count", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Column(
            if (labelOnGlass) Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(if (compact) 6.dp else 10.dp)
                .glassOnImage(rs(if (compact) 16.dp else 22.dp))
                .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 5.dp else 8.dp)
            else Modifier.align(Alignment.BottomStart).padding(if (compact) 8.dp else 14.dp),
        ) {
            Text(
                name, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
            )
            if (!labelOnGlass) Text(
                "$count videos", color = fg.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<PlaylistEntity>,
    onOpen: (PlaylistEntity) -> Unit, onDelete: (PlaylistEntity) -> Unit,
) {
    if (playlists.isEmpty()) { CenterMessage("Aún no tienes listas. Toca + para crear una."); return }
    val seen = remember { mutableSetOf<Any>() }
    LazyColumn(
        contentPadding = pad(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(playlists, key = { _, p -> p.id }) { i, p ->
            ListItem(
                modifier = (if (Perf.enabled) Modifier else Modifier.animateItem()).popIn(p.id, i, seen)
                    .bounceClick(MaterialTheme.shapes.large) { onOpen(p) }
                    .glass(MaterialTheme.shapes.large),
                leadingContent = { Icon(Icons.Rounded.Folder, null) },
                headlineContent = { Text(p.name) },
                trailingContent = {
                    IconButton(onClick = { onDelete(p) }) { Icon(Icons.Rounded.Delete, "Eliminar lista") }
                },
            )
        }
    }
}

@Composable
private fun PlaylistDetail(
    dao: PlaylistDao, pl: PlaylistEntity,
    onPlay: (List<String>, List<String>, Int) -> Unit,
) {
    val flow = remember(pl.id) { dao.items(pl.id) }
    val items by flow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    fun save(list: List<PlaylistItemEntity>) { scope.launch { dao.reorder(pl.id, list) } }
    val seen = remember { mutableSetOf<Any>() }
    val keys = remember(items) {
        val count = HashMap<String, Int>()
        items.map { e -> "${e.videoUri}#${count.merge(e.videoUri, 1, Int::plus)}" }
    }
    val st = LocalUcdStyle.current
    val cardShape = rs(36.dp)
    // Botones inferiores: píldora oscura con borde fino de luz (como "Create new" de la referencia)
    val pillBg = if (st.navBg != Color.Unspecified) st.navBg.copy(alpha = 0.96f) else Color(0xFF1E2430)

    Box(Modifier.fillMaxSize()) {
        // Segunda tarjeta, asomando por detrás de la principal
        Box(
            Modifier.fillMaxSize()
                .padding(start = 30.dp, end = 30.dp, top = 2.dp, bottom = 112.dp)
                .clip(cardShape)
                .background(st.panel.copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.35f), cardShape),
        )
        // Tarjeta principal con los videos de la lista
        Column(
            Modifier.fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 112.dp)
                .clip(cardShape)
                .background(st.panel)
                .border(1.dp, Color.White.copy(alpha = 0.5f), cardShape),
        ) {
            Text(
                if (items.isEmpty()) "Lista vacía" else if (items.size == 1) "1 video" else "${items.size} videos",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 4.dp),
            )
            if (items.isEmpty()) {
                Text(
                    "Añade videos con ⋮ dentro de una carpeta.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(items, key = { i, _ -> keys.getOrElse(i) { "p$i" } }) { i, e ->
                    ListItem(
                        modifier = (if (Perf.enabled) Modifier else Modifier.animateItem()).popIn(keys.getOrElse(i) { "p$i" }, i, seen)
                            .bounceClick(rs(24.dp)) {
                                onPlay(items.map { it.videoUri }, items.map { it.title }, i)
                            }
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                        leadingContent = { VideoThumb(e.videoUri, Modifier.size(96.dp, 54.dp)) },
                        headlineContent = { Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            Row {
                                IconButton(enabled = i > 0, modifier = Modifier.size(38.dp), onClick = {
                                    val m = items.toMutableList(); m.add(i - 1, m.removeAt(i)); save(m)
                                }) { Icon(Icons.Rounded.ArrowUpward, "Subir", Modifier.size(20.dp)) }
                                IconButton(enabled = i < items.lastIndex, modifier = Modifier.size(38.dp), onClick = {
                                    val m = items.toMutableList(); m.add(i + 1, m.removeAt(i)); save(m)
                                }) { Icon(Icons.Rounded.ArrowDownward, "Bajar", Modifier.size(20.dp)) }
                                IconButton(modifier = Modifier.size(38.dp), onClick = {
                                    val m = items.toMutableList(); m.removeAt(i); save(m)
                                }) { Icon(Icons.Rounded.Delete, "Quitar", Modifier.size(20.dp)) }
                            }
                        },
                    )
                }
            }
        }
        // Reproducir todo (píldora) + aleatorio (círculo)
        AnimatedVisibility(
            visible = items.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
            enter = scaleIn(spring(0.6f, Spring.StiffnessMediumLow), initialScale = 0.7f) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.height(56.dp)
                        .bounceClick(CircleShape) { onPlay(items.map { it.videoUri }, items.map { it.title }, 0) }
                        .background(pillBg)
                        .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Reproducir todo", color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
                Box(
                    Modifier.size(56.dp)
                        .bounceClick(CircleShape) {
                            val sh = items.shuffled()
                            onPlay(sh.map { it.videoUri }, sh.map { it.title }, 0)
                        }
                        .background(pillBg)
                        .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Shuffle, "Aleatorio", tint = Color.White) }
            }
        }
    }
}

private fun two(n: Long) = if (n < 10) "0$n" else "$n"

private fun fmt(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "$h:${two(m)}:${two(sec)}" else "${two(m)}:${two(sec)}"
}

/**
 * Abre el menú de transmisión de pantalla del celular. Cada marca lo llama distinto (Smart View,
 * Cast, Proyectar...), así que se prueban varias pantallas de ajustes y, si ninguna existe,
 * se avisa cómo abrirlo desde el panel de ajustes rápidos.
 */
private fun openCastMenu(ctx: Context) {
    val actions = listOf(
        "android.settings.CAST_SETTINGS",
        "com.samsung.android.smartview.action.SMART_VIEW",
        "android.settings.WIFI_DISPLAY_SETTINGS",
    )
    for (action in actions) {
        try {
            ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
            // esta pantalla no existe en este celular: se prueba la siguiente
        }
    }
    android.widget.Toast.makeText(
        ctx, "Desliza el panel de arriba y toca Transmitir, Smart View o Proyectar",
        android.widget.Toast.LENGTH_LONG,
    ).show()
}

private fun fmtSize(bytes: Long): String = when {
    bytes >= (1L shl 30) -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= (1L shl 20) -> "${bytes shr 20} MB"
    else -> "${bytes shr 10} KB"
}

private val dateFormat: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

private fun fmtDate(sec: Long): String = dateFormat.format(Date(sec * 1000))
