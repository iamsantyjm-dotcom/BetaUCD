package com.ucd.player.player

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.ucd.player.data.Perf
import com.ucd.player.data.UcdPrefs
import com.ucd.player.ui.VideoThumb
import com.ucd.player.ui.prefetchThumbs
import com.ucd.player.ui.theme.GlassAlertDialog
import com.ucd.player.ui.theme.LocalUcdStyle
import com.ucd.player.ui.theme.glass
import com.ucd.player.ui.theme.rs
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** Acción personalizada que entiende PlaybackService para saltar en videos "no buscables". */
const val SEEK_ACTION = "ucd.seek"

private const val HIDE_DELAY_MS = 1500L
private const val SWIPE_FULL_WIDTH_MS = 90_000f
private const val EDGE_SAFE_FRACTION = 0.14f
private const val HORIZONTAL_DOMINANCE = 1.5f

enum class VideoScale(val label: String) {
    Fit("Ajustar"), Fill("Rellenar"), Stretch("Estirar");
    fun next() = entries[(ordinal + 1) % entries.size]
}

@Composable
fun PlayerScreen(
    player: Player,
    onBack: () -> Unit,
    setImmersive: (Boolean) -> Unit,
    volumeKeyEvents: Flow<Unit> = emptyFlow(),
    pip: Boolean = false,
    onEnterPip: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val prefs = remember { UcdPrefs(ctx) }
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var scale by remember { mutableStateOf(VideoScale.entries.firstOrNull { it.name == prefs.videoScale } ?: VideoScale.Fit) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var positionMs by remember { mutableLongStateOf(player.currentPosition) }
    var durationMs by remember { mutableLongStateOf(player.duration.coerceAtLeast(0)) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var error by remember { mutableStateOf<String?>(null) }
    var tracks by remember { mutableStateOf(player.currentTracks) }
    val multiAudio = remember(tracks) { trackOptions(tracks, C.TRACK_TYPE_AUDIO).size > 1 }
    var tracksDialog by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(player.currentMediaItemIndex) }
    var repeatMode by remember { mutableIntStateOf(player.repeatMode) }
    var shuffleOn by remember { mutableStateOf(player.shuffleModeEnabled) }
    BackHandler(enabled = queueOpen && !pip) { queueOpen = false }
    LaunchedEffect(pip) { if (pip) queueOpen = false }
    var title by remember { mutableStateOf(player.mediaMetadata.title?.toString()) }
    // Video en la pantalla externa (TV): el celular queda con los controles
    val activity = remember { ctx.findActivity() }
    var castPref by remember { mutableStateOf(prefs.videoOnExternal) }
    val externalConnected = Perf.externalConnected && activity != null
    val castOn = castPref && externalConnected
    // El TV sigue con el video aunque salgas del reproductor (así la lista de carpetas no se ve en el TV)
    DisposableEffect(castOn, player) {
        if (castOn) VideoCast.start(activity!!, player) else VideoCast.stop()
        onDispose { }
    }
    DisposableEffect(Unit) {
        VideoCast.screenOpen = true
        onDispose {
            VideoCast.screenOpen = false
            VideoCast.fade = 1f
            VideoCast.closeIfIdle()
        }
    }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudStamp by remember { mutableIntStateOf(0) }
    var hudLast by remember { mutableStateOf("") }
    LaunchedEffect(hud) { hud?.let { hudLast = it } }
    
    var levelOnLeft by remember { mutableStateOf(false) }
    var levelValue by remember { mutableFloatStateOf(0f) }
    var levelVisible by remember { mutableStateOf(false) }
    var levelStamp by remember { mutableIntStateOf(0) }
    var mediaId by remember { mutableStateOf(player.currentMediaItem?.mediaId ?: "") }
    var fallbackDuration by remember { mutableLongStateOf(0L) }
    var unseekable by remember { mutableStateOf(false) }
    var remuxBusy by remember { mutableStateOf(false) }
    val handled = remember { mutableStateListOf<String>() }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var pendingSwap by remember { mutableStateOf<Pair<String, java.io.File>?>(null) }
    var downInSafeZone by remember { mutableStateOf(false) }
    // Fundido a negro de la imagen (1 = normal, 0 = negro), a la par del volumen
    var fadeLevel by remember { mutableFloatStateOf(1f) }
    var blackout by remember { mutableStateOf(VideoCast.active && VideoCast.blackout) }   // pantalla en negro (el video sigue)
    // Zoom del video con dos dedos (1 = normal) y desplazamiento en píxeles
    var vZoom by remember { mutableFloatStateOf(1f) }
    var vPan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(mediaId) { vZoom = 1f; vPan = Offset.Zero }
    LaunchedEffect(player) {
        while (true) {
            val ms = prefs.fadeMs
            fadeLevel = if (ms > 0 && prefs.fadePicture) {
                val pos = player.currentPosition
                val dur = player.duration
                // La entrada solo oscurece mientras se reproduce (en pausa al inicio se ve el cuadro)
                if (player.isPlaying) Fade.factor(pos, dur, ms) else Fade.fadeOut(pos, dur, ms)
            } else 1f
            VideoCast.fade = fadeLevel
            delay(50)
        }
    }
    LaunchedEffect(scale, VideoCast.active) { VideoCast.scale = scale }
    LaunchedEffect(blackout, VideoCast.active) { VideoCast.blackout = blackout }

    fun applySwap(src: String, out: java.io.File, posMs: Long) {
        pendingSwap = null
        if (player.currentMediaItem?.mediaId != src) return
        val idx = player.currentMediaItemIndex
        val old = player.getMediaItemAt(idx)
        player.replaceMediaItem(idx, old.buildUpon().setUri(Uri.fromFile(out)).build())
        player.seekTo(idx, posMs)
        handled.add(src)
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onEvents(p: Player, e: Player.Events) {
                mediaId = p.currentMediaItem?.mediaId ?: ""
                currentIndex = p.currentMediaItemIndex
            }
            override fun onPlayerError(e: PlaybackException) {
                error = "${e.errorCodeName}\n${e.message}"
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { error = null }
            override fun onRepeatModeChanged(mode: Int) { repeatMode = mode }
            override fun onShuffleModeEnabledChanged(enabled: Boolean) { shuffleOn = enabled }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                title = mediaMetadata.title?.toString()
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    LaunchedEffect(controlsVisible, interaction) {
        if (controlsVisible) {
            delay(prefs.controlsHideMs.toLong())
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible) { setImmersive(!controlsVisible) }
    LaunchedEffect(mediaId) {
        fallbackDuration = if (mediaId.isEmpty()) 0L
        else withContext(Dispatchers.IO) { queryStoreDuration(ctx, mediaId) }
    }
    LaunchedEffect(mediaId, unseekable) {
        if (unseekable && mediaId.isNotEmpty() && mediaId !in handled && !remuxBusy &&
            pendingSwap?.first != mediaId && player !is MediaController) {
            val src = mediaId
            remuxBusy = true
            try {
                val out = Remuxer.remux(ctx, src)
                if (out == null) {
                    handled.add(src)
                } else if (player.currentMediaItem?.mediaId == src) {
                    if (player.isPlaying) pendingSwap = src to out
                    else applySwap(src, out, player.currentPosition)
                }
            } finally {
                remuxBusy = false
            }
        }
    }
    LaunchedEffect(isPlaying, pendingSwap) {
        val p = pendingSwap ?: return@LaunchedEffect
        if (!isPlaying) applySwap(p.first, p.second, player.currentPosition)
    }
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition
            val d = player.duration
            durationMs = if (d > 0) d else fallbackDuration
            if (player.playbackState == Player.STATE_READY) unseekable = !player.isCurrentMediaItemSeekable
            delay(300)
        }
    }
    LaunchedEffect(levelStamp) {
        if (levelVisible) {
            delay(1200) // Un poco más de tiempo visible para que se lea el porcentaje
            levelVisible = false
        }
    }
    LaunchedEffect(hudStamp) {
        if (hud != null) {
            delay(800)
            hud = null
        }
    }

    fun poke() { controlsVisible = true; interaction++ }
    fun showLevel(onLeft: Boolean, value: Float) {
        levelOnLeft = onLeft; levelValue = value.coerceIn(0f, 2f)
        levelVisible = true; levelStamp++
    }

    // El volumen total ahora es una combinación del hardware (0-1) y software (1-2)
    fun currentVolume(): Float {
        val maxV = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val hwVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxV
        return if (PlaybackService.boost > 1.001f) PlaybackService.boost else hwVol
    }
    
    // Control de teclas físicas: Si usan el botón físico, devolvemos el control 100% al celular
    LaunchedEffect(volumeKeyEvents) {
        volumeKeyEvents.collect { 
            showLevel(false, currentVolume()) 
        }
    }

    fun showHud(t: String) { hud = t; hudStamp++ }

    // El zoom con dos dedos también se aplica al video que se ve en el TV (como fracción de la pantalla)
    LaunchedEffect(Unit) {
        var prev = 1f
        snapshotFlow { Triple(vZoom, vPan, boxSize) }.collect { (z, p, s) ->
            VideoCast.setZoom(
                z,
                if (s.width > 0) p.x / s.width else 0f,
                if (s.height > 0) p.y / s.height else 0f,
            )
            if (VideoCast.active && abs(z - prev) > 0.001f) showHud("Zoom ${(z * 100).roundToInt()} %")
            prev = z
        }
    }
    LaunchedEffect(VideoCast.active) {
        val s = boxSize
        VideoCast.setZoom(
            vZoom,
            if (s.width > 0) vPan.x / s.width else 0f,
            if (s.height > 0) vPan.y / s.height else 0f,
        )
    }
    // Precarga de las miniaturas de la cola (ajuste opcional)
    LaunchedEffect(player) {
        if (prefs.preload) {
            delay(3000)
            prefetchThumbs(ctx, List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId }.filter { it.isNotEmpty() })
        }
    }
    fun doSeek(ms: Long) {
        val target = ms.coerceAtLeast(0L)
        val pend = pendingSwap
        if (pend != null && pend.first == mediaId) {
            applySwap(pend.first, pend.second, target)
            positionMs = target
            return
        }
        if (unseekable && mediaId !in handled && player !is MediaController) return
        if (player is MediaController && !player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            player.sendCustomCommand(SessionCommand(SEEK_ACTION, Bundle.EMPTY), Bundle().apply { putLong("pos", target) })
        } else {
            player.seekTo(target)
        }
        positionMs = target
    }
    
    fun prevVideo() {
        if (player.hasPreviousMediaItem()) player.seekToDefaultPosition(player.previousMediaItemIndex)
        else doSeek(0)
    }
    
    fun nextVideo() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem() else showHud("Último video")
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { boxSize = it }
                .pointerInput(Unit) {
                    // Zoom con dos dedos: pellizcar para acercar y mover con los dos dedos
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                val zc = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zc != 1f || pan != Offset.Zero) {
                                    val cen = event.calculateCentroid()
                                    val c = Offset(boxSize.width / 2f, boxSize.height / 2f)
                                    val nz = (vZoom * zc).coerceIn(1f, 4f)
                                    val cn = cen - c
                                    val raw = cn - (cn - vPan) * (nz / vZoom) + pan
                                    val mx = boxSize.width * (nz - 1f) / 2f
                                    val my = boxSize.height * (nz - 1f) / 2f
                                    vZoom = nz
                                    vPan = Offset(raw.x.coerceIn(-mx, mx), raw.y.coerceIn(-my, my))
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            val height = boxSize.height.toFloat().coerceAtLeast(1f)
                            downInSafeZone = offset.y >= height * EDGE_SAFE_FRACTION && offset.y <= height * (1f - EDGE_SAFE_FRACTION)
                            tryAwaitRelease()
                        },
                        onTap = {
                            // Con la pantalla en negro, tocar solo muestra/oculta los controles:
                            // el negro se quita únicamente con el ojo (igual que en la Biblia)
                            if (blackout || downInSafeZone) {
                                if (controlsVisible) controlsVisible = false else poke()
                            }
                        },
                        onDoubleTap = { offset ->
                            if (downInSafeZone) {
                                val w = boxSize.width
                                when {
                                    offset.x < w * 0.3f -> { prevVideo(); showHud("Video anterior") }
                                    offset.x > w * 0.7f -> { nextVideo(); showHud("Siguiente video") }
                                    else -> { if (player.isPlaying) player.pause() else player.play() }
                                }
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val height = boxSize.height.toFloat().coerceAtLeast(1f)
                        val pointerId = down.id
                        val inSafeZone = down.position.y >= height * EDGE_SAFE_FRACTION &&
                            down.position.y <= height * (1f - EDGE_SAFE_FRACTION)

                        var mode = if (inSafeZone && prefs.swipeGestures) 0 else 2
                        var totalX = 0f
                        var totalY = 0f
                        val slop = viewConfiguration.touchSlop
                        while (mode == 0) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null || !change.pressed) return@awaitEachGesture
                            if (change.isConsumed) { mode = 2; break }
                            val delta = change.positionChange()
                            totalX += delta.x
                            totalY += delta.y
                            if (kotlin.math.sqrt(totalX * totalX + totalY * totalY) >= slop) {
                                mode = if (abs(totalX) > abs(totalY) * HORIZONTAL_DOMINANCE) 1 else 2
                                if (mode == 1) change.consume()
                            }
                        }

                        if (mode == 2) {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                            }
                            return@awaitEachGesture
                        }

                        val startPos = player.currentPosition
                        var target = startPos
                        var total = totalX
                        fun updateSeek() {
                            val dur = durationMs
                            val max = if (dur > 0) dur else Long.MAX_VALUE
                            target = (startPos + (total / boxSize.width.coerceAtLeast(1) * SWIPE_FULL_WIDTH_MS).toLong())
                                .coerceIn(0L, max)
                            val d = target - startPos
                            seekPreview = target
                            showHud((if (d >= 0) "+" else "-") + formatTime(abs(d)))
                        }
                        updateSeek()
                        val finished = drag(pointerId) { change ->
                            total += change.positionChange().x
                            change.consume()
                            updateSeek()
                        }
                        if (finished) doSeek(target)
                        seekPreview = null
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val width = boxSize.width.toFloat().coerceAtLeast(1f)
                        val height = boxSize.height.toFloat().coerceAtLeast(1f)
                        val edgeWidth = width * 0.14f
                        val safeTop = height * EDGE_SAFE_FRACTION
                        val safeBottom = height * (1f - EDGE_SAFE_FRACTION)
                        val left = down.position.x <= edgeWidth
                        val right = down.position.x >= width - edgeWidth
                        val allowed = prefs.swipeGestures && (left || right) &&
                            down.position.y >= safeTop && down.position.y <= safeBottom

                        if (!allowed) {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                            }
                        } else {
                            var startVal = currentVolume()
                            var acc = 0f
                            val pointerId = down.id
                            val slop = awaitTouchSlopOrCancellation(pointerId) { change, overSlop ->
                                val delta = change.positionChange()
                                if (kotlin.math.abs(delta.y) > kotlin.math.abs(delta.x)) {
                                    change.consume()
                                    acc -= overSlop.y
                                }
                            }

                            if (slop != null) {
                                startVal = currentVolume()
                                val maxV = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                fun applyVolume() {
                                    // Ampliamos el tope a 2.0f (200%). Multiplicamos x1.5 para mayor sensibilidad al subir
                                    val v = (startVal + acc / height * 1.5f).coerceIn(0f, 2f)
                                    runCatching {
                                        if (v <= 1f) {
                                            // 0% a 100%: Control nativo de hardware
                                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxV).roundToInt(), 0)
                                            PlaybackService.boost = 1f
                                        } else {
                                            // 101% a 200%: Hardware al tope, ExoPlayer hace la magia (Súper Volumen)
                                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, maxV, 0)
                                            PlaybackService.boost = v
                                        }
                                    }
                                    showLevel(left, v)
                                }
                                applyVolume()
                                drag(pointerId) { change ->
                                    if (change.pressed) {
                                        val delta = change.positionChange()
                                        change.consume()
                                        acc -= delta.y
                                        applyVolume()
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            if (castOn && VideoCast.active) {
                // El video se ve en el TV: aquí solo queda un aviso discreto
                if (!pip) {
                    Text(
                        "Reproduciendo en la pantalla externa",
                        color = Color.White.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            } else {
                VideoSurface(
                    player, scale,
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = vZoom
                        scaleY = vZoom
                        translationX = vPan.x
                        translationY = vPan.y
                    },
                )
            }

            error?.let {
                Text(
                    "Error al reproducir:\n$it",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }

            // Fundido a negro entre videos (no recompone: solo se vuelve a dibujar)
            Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black, alpha = 1f - fadeLevel) })

            // Pantalla en negro: solo se quita con el ojo
            if (blackout) Box(Modifier.fillMaxSize().background(Color.Black))

            AnimatedVisibility(
                visible = hud != null && !pip,
                enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), initialScale = 0.75f) + fadeIn(),
                exit = scaleOut(targetScale = 0.9f) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 10.dp),
            ) {
                Surface(
                    shape = rs(12.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                ) {
                    Text(
                        hud ?: hudLast, color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = levelVisible && !pip,
                enter = scaleIn(initialScale = 0.6f) + fadeIn(), exit = scaleOut(targetScale = 0.8f) + fadeOut(),
                modifier = Modifier
                    .align(if (levelOnLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 18.dp),
            ) {
                LevelIndicator(levelValue)
            }

            if (!pip && !blackout) Text(
                text = "-" + formatTime((durationMs - (seekPreview ?: positionMs)).coerceAtLeast(0L)),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding()
                    .padding(end = 12.dp, bottom = 8.dp),
            )

            AnimatedVisibility(
                visible = controlsVisible && !pip,
                enter = fadeIn(tween(180)), exit = fadeOut(tween(220)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(
                        Modifier.align(Alignment.TopStart).padding(12.dp).animateEnterExit(
                            enter = slideInVertically(spring(0.7f, Spring.StiffnessMediumLow)) { -it } + fadeIn(),
                            exit = slideOutVertically(tween(200)) { -it } + fadeOut(),
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            onClick = onBack, shape = CircleShape,
                            color = Color.Transparent, contentColor = Color.White,
                            modifier = Modifier.glass(CircleShape, dark = true),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", Modifier.padding(8.dp).size(20.dp))
                        }
                        title?.let {
                            Surface(
                                shape = rs(16.dp),
                                color = Color.Transparent, contentColor = Color.White,
                                modifier = Modifier.glass(rs(16.dp), dark = true),
                            ) {
                                Text(
                                    it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.widthIn(max = 320.dp)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }

                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).animateEnterExit(
                            enter = slideInVertically(spring(0.65f, Spring.StiffnessMediumLow)) { it / 2 } + scaleIn(initialScale = 0.9f) + fadeIn(),
                            exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(),
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(Modifier.fillMaxWidth(0.7f)) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 1.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = formatTime(seekPreview ?: positionMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.88f),
                                )
                                Text(
                                    text = formatTime(durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.88f),
                                )
                            }
                            SeekBar(
                                position = positionMs, duration = durationMs,
                                onSeek = { doSeek(it) },
                                onPreview = { seekPreview = it },
                                onTouch = { poke() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Surface(
                            shape = rs(28.dp),
                            color = Color.Transparent, contentColor = Color.White,
                            modifier = Modifier.glass(rs(28.dp), dark = true),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                IconButton(onClick = { prevVideo(); poke() }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Rounded.SkipPrevious, "Anterior", Modifier.size(20.dp))
                                }
                                FilledIconButton(
                                    onClick = { if (isPlaying) player.pause() else player.play(); poke() },
                                    modifier = Modifier.size(42.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = LocalUcdStyle.current.videoAccent, contentColor = Color.Black,
                                    ),
                                ) {
                                    AnimatedContent(
                                        targetState = isPlaying,
                                        transitionSpec = {
                                            (scaleIn(spring(0.5f, Spring.StiffnessMedium), initialScale = 0.4f) + fadeIn(tween(120))) togetherWith
                                                (scaleOut(tween(120), targetScale = 0.4f) + fadeOut(tween(100)))
                                        },
                                        label = "playpause",
                                    ) { playing ->
                                        Icon(
                                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            if (playing) "Pausar" else "Reproducir",
                                            Modifier.size(24.dp),
                                        )
                                    }
                                }
                                IconButton(onClick = { nextVideo(); poke() }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Rounded.SkipNext, "Siguiente", Modifier.size(20.dp))
                                }
                                FilledTonalIconButton(
                                    onClick = { scale = scale.next(); prefs.videoScale = scale.name; poke() },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White,
                                    ),
                                ) {
                                    Icon(
                                        when (scale) {
                                            VideoScale.Fit -> Icons.Rounded.FitScreen
                                            VideoScale.Fill -> Icons.Rounded.Crop
                                            VideoScale.Stretch -> Icons.Rounded.AspectRatio
                                        },
                                        "Tamaño: ${scale.label}",
                                        Modifier.size(18.dp),
                                    )
                                }
                                if (multiAudio) {
                                    IconButton(onClick = { tracksDialog = true; poke() }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Rounded.Audiotrack, "Pista de audio", Modifier.size(18.dp))
                                    }
                                }
                                if (externalConnected) {
                                    IconButton(
                                        onClick = { castPref = !castPref; prefs.videoOnExternal = castPref; poke() },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            if (castOn) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                                            if (castOn) "Ver el video en el celular" else "Ver el video en la pantalla externa",
                                            Modifier.size(18.dp),
                                        )
                                    }
                                }
                                IconButton(onClick = { queueOpen = true; controlsVisible = false }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.VideoLibrary, "Cola de reproducción", Modifier.size(18.dp))
                                }
                                IconButton(onClick = onEnterPip, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.PictureInPictureAlt, "Imagen en imagen", Modifier.size(18.dp))
                                }
                                IconButton(onClick = { blackout = !blackout }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        if (blackout) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                        if (blackout) "Quitar pantalla en negro" else "Pantalla en negro",
                                        Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        QueuePanel(
            visible = queueOpen && !pip,
            player = player,
            currentIndex = currentIndex,
            repeatMode = repeatMode,
            shuffleOn = shuffleOn,
            onClose = { queueOpen = false },
        )
    }

    if (tracksDialog && !pip) {
        TracksDialog(tracks = tracks, player = player, onClose = { tracksDialog = false })
    }
}

private class TrackOpt(val label: String, val group: Tracks.Group, val index: Int, val selected: Boolean)

private fun trackOptions(tracks: Tracks, type: Int): List<TrackOpt> {
    val out = mutableListOf<TrackOpt>()
    var n = 0
    for (g in tracks.groups) {
        if (g.type != type) continue
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            n++
            val f = g.getTrackFormat(i)
            val lang = f.language?.takeIf { it != "und" }
                ?.let { Locale.forLanguageTag(it).displayLanguage }?.takeIf { it.isNotBlank() }
            val label = listOfNotNull(f.label, lang).distinct().joinToString(" · ")
                .ifBlank { (if (type == C.TRACK_TYPE_AUDIO) "Audio " else "Subtítulo ") + n }
            out += TrackOpt(label, g, i, g.isTrackSelected(i))
        }
    }
    return out
}

private fun selectTrack(player: Player, type: Int, opt: TrackOpt?) {
    val b = player.trackSelectionParameters.buildUpon()
    if (opt == null) {
        b.setTrackTypeDisabled(type, true)
    } else {
        b.setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(opt.group.mediaTrackGroup, opt.index))
    }
    player.trackSelectionParameters = b.build()
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(10.dp))
        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TracksDialog(tracks: Tracks, player: Player, onClose: () -> Unit) {
    val audio = trackOptions(tracks, C.TRACK_TYPE_AUDIO)
    GlassAlertDialog(
        dark = true,
        onDismissRequest = onClose,
        title = { Text("Pista de audio") },
        text = {
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                if (audio.isEmpty()) Text("Sin pistas", style = MaterialTheme.typography.bodySmall)
                audio.forEach { o -> TrackRow(o.label, o.selected) { selectTrack(player, C.TRACK_TYPE_AUDIO, o) } }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } },
    )
}

/** Indicador compacto de volumen para no tapar el video durante una proyección. */
@Composable
private fun LevelIndicator(value: Float) {
    val isSuper = value > 1f
    val color = if (isSuper) Color(0xFFFF9800) else Color.White
    val icon = when {
        value <= 0f -> Icons.Rounded.VolumeOff
        value < 0.35f -> Icons.Rounded.VolumeDown
        else -> Icons.Rounded.VolumeUp
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.62f),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Volumen",
                tint = color,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = "${(value * 100).toInt()}%",
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SeekBar(
    position: Long, duration: Long,
    onSeek: (Long) -> Unit, onPreview: (Long?) -> Unit, onTouch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val frac = dragFrac ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val active = LocalUcdStyle.current.videoAccent
    val currentDuration by rememberUpdatedState(duration)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnPreview by rememberUpdatedState(onPreview)
    val currentOnTouch by rememberUpdatedState(onTouch)

    Box(
        modifier
            .height(36.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val w = size.width.coerceAtLeast(1).toFloat()
                    fun fractionOf(x: Float) = (x / w).coerceIn(0f, 1f)
                    currentOnTouch()
                    dragFrac = fractionOf(down.position.x)
                    currentOnPreview((dragFrac!! * currentDuration).toLong())
                    drag(down.id) { change ->
                        change.consume()
                        dragFrac = fractionOf(change.position.x)
                        currentOnPreview((dragFrac!! * currentDuration).toLong())
                        currentOnTouch()
                    }
                    val f = dragFrac
                    if (f != null && currentDuration > 0) currentOnSeek((f * currentDuration).toLong())
                    dragFrac = null
                    currentOnPreview(null)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)))
        Box(Modifier.fillMaxWidth(frac).height(3.dp).clip(CircleShape).background(active))
        Row(Modifier.fillMaxWidth(frac), horizontalArrangement = Arrangement.End) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(active))
        }
    }
}

@Composable
internal fun VideoSurface(player: Player, scale: VideoScale, modifier: Modifier) {
    var vs by remember { mutableStateOf(player.videoSize) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) { vs = videoSize }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    val ratio = if (vs.width > 0 && vs.height > 0)
        vs.width * vs.pixelWidthHeightRatio / vs.height else 16f / 9f

    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val boxRatio = maxWidth.value / maxHeight.value
        val sizeModifier = when (scale) {
            VideoScale.Stretch -> Modifier.fillMaxSize()
            VideoScale.Fit ->
                if (boxRatio > ratio) Modifier.requiredSize(maxHeight * ratio, maxHeight)
                else Modifier.requiredSize(maxWidth, maxWidth / ratio)
            VideoScale.Fill ->
                if (boxRatio > ratio) Modifier.requiredSize(maxWidth, maxWidth / ratio)
                else Modifier.requiredSize(maxHeight * ratio, maxHeight)
        }
        AndroidView(
            modifier = sizeModifier,
            factory = { c -> TextureView(c).also { player.setVideoTextureView(it) } },
            onRelease = { player.clearVideoTextureView(it) },
        )
    }
}

private fun queryStoreDuration(ctx: Context, uri: String): Long = runCatching {
    ctx.contentResolver.query(
        Uri.parse(uri), arrayOf(MediaStore.Video.Media.DURATION), null, null, null,
    )?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
}.getOrDefault(0L)

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

@Composable
private fun BoxScope.QueuePanel(
    visible: Boolean,
    player: Player,
    currentIndex: Int,
    repeatMode: Int,
    shuffleOn: Boolean,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    AnimatedVisibility(
        visible = visible,
        enter = if (Perf.enabled) EnterTransition.None else fadeIn(tween(180)),
        exit = if (Perf.enabled) ExitTransition.None else fadeOut(tween(160)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (Perf.enabled) EnterTransition.None else slideInVertically(spring(0.8f, Spring.StiffnessMediumLow)) { it } + fadeIn(),
        exit = if (Perf.enabled) ExitTransition.None else slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
        modifier = Modifier.align(Alignment.BottomCenter)
            .fillMaxHeight(0.94f)
            .fillMaxWidth(0.62f)
            .widthIn(max = 520.dp),
    ) {
        val items = remember { List(player.mediaItemCount) { player.getMediaItemAt(it) } }
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = (currentIndex - 1).coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        )
        Column(
            Modifier.fillMaxSize()
                .clip(shape)
                .background(Color.Black.copy(alpha = 0.55f))
                .glass(shape, dark = true)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    var acc = 0f
                    detectVerticalDragGestures(
                        onDragStart = { acc = 0f },
                        onDragEnd = { if (acc > 60f) onClose() },
                        onDragCancel = { },
                        onVerticalDrag = { change, dy -> change.consume(); acc += dy },
                    )
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.padding(top = 10.dp).width(44.dp).height(4.dp)
                        .clip(CircleShape).background(Color.White.copy(alpha = 0.35f)),
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Cola de reproducción",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    val repeatOn = repeatMode != Player.REPEAT_MODE_OFF
                    IconButton(
                        onClick = {
                            player.repeatMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        },
                        modifier = Modifier.size(40.dp)
                            .background(if (repeatOn) Color.White.copy(alpha = 0.18f) else Color.Transparent, CircleShape),
                    ) {
                        Icon(
                            if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            "Repetir",
                            tint = if (repeatOn) Color.White else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = { player.shuffleModeEnabled = !shuffleOn },
                        modifier = Modifier.size(40.dp)
                            .background(if (shuffleOn) Color.White.copy(alpha = 0.18f) else Color.Transparent, CircleShape),
                    ) {
                        Icon(
                            Icons.Rounded.Shuffle, "Aleatorio",
                            tint = if (shuffleOn) Color.White else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(items, key = { i, _ -> i }) { i, item ->
                    val current = i == currentIndex
                    val id = item.mediaId
                    val duration by produceState(0L, id) {
                        value = withContext(Dispatchers.IO) { queryStoreDuration(ctx, id) }
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(rs(18.dp))
                            .background(if (current) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable {
                                player.seekToDefaultPosition(i)
                                player.play()
                                onClose()
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(112.dp, 63.dp)) {
                            VideoThumb(id, Modifier.fillMaxSize(), corner = 12.dp)
                            if (current) {
                                Box(
                                    Modifier.fillMaxSize().clip(rs(12.dp))
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.mediaMetadata.title?.toString() ?: "Video ${i + 1}",
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = Color.White,
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (duration > 0) {
                                Text(
                                    formatTime(duration),
                                    color = Color.White.copy(alpha = 0.65f),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
