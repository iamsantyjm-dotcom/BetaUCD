package com.ucd.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp

/** Escala de esquinas del tema activo (se actualiza en UcdTheme antes de dibujar la pantalla). */
object UcdRuntime {
    @Volatile
    var corner: Float = 1f
}

/** Esquinas redondeadas que se adaptan al tema (cuadradas en unos, muy redondas en otros). */
fun rs(radius: Dp): RoundedCornerShape = RoundedCornerShape(radius * UcdRuntime.corner)

/** Mancha de luz del fondo: posición y tamaño como fracción del ancho/alto de la pantalla. */
@Immutable
class Glow(val color: Color, val cx: Float, val cy: Float, val radius: Float)

/**
 * Un tema completo: colores, fondo, tipo de superficie (cristal o plana), esquinas y letra.
 * Todo lo que se ve en la app sale de aquí, así que al cambiar de tema cambia todo.
 */
@Immutable
class UcdStyle(
    val id: String,
    val name: String,
    val description: String,
    val dark: Boolean,
    // Fondo
    val bg: List<Color>,
    val glows: List<Glow> = emptyList(),
    // Colores
    val fg: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val videoAccent: Color,
    val christRed: Color,
    // Superficies planas (sin cristal)
    val flat: Boolean = false,
    val surface: Color,
    val outline: Color,
    val panel: Color,
    // Cristal (por defecto: cristal oscuro neutro)
    val glassBase: Color = Color.Black.copy(alpha = 0.14f),
    val glassTint: Color = Color.White,
    val fillTop: Float = 0.15f,
    val fillBottom: Float = 0.04f,
    val edgeA: Color = Color.White.copy(alpha = 0.55f),
    val edgeB: Color = Color.White.copy(alpha = 0.05f),
    val edgeC: Color = Color.White.copy(alpha = 0.28f),
    val sheen: Float = 0.10f,
    val glow: Float = 0.55f,
    val rim: Float = 0f,   // borde grueso translúcido (opacidad), típico del cristal esmerilado
    // Barra inferior oscura, botón flotante y tarjetas pastel (opcionales)
    val navBg: Color = Color.Unspecified,
    val navMuted: Color = Color.Unspecified,
    val fabBg: Color = Color.Unspecified,
    val fabFg: Color = Color.Unspecified,
    val pastels: List<Color> = emptyList(),
    val pastelText: Color = Color(0xFF1B2540),
    // Pestaña seleccionada de la barra inferior
    val selFill: Color,
    val selBorder: Color,
    val selText: Color,
    // Forma y letra
    val corner: Float = 1f,
    val font: FontFamily = FontFamily.Default,
    val titleWeight: FontWeight? = null,
    val titleSize: Float = 18f,   // tamaño del título de la barra superior (sp)
    val twin: String? = null,     // id de la versión clara u oscura de este mismo tema
)

object Themes {
    val Graphite = UcdStyle(
        id = "graphite", name = "Grafito",
        description = "Negro grafito con un halo cobre y cristal sutil.",
        dark = true,
        bg = listOf(Color(0xFF111114), Color(0xFF0B0B0E), Color(0xFF0D0C0E)),
        glows = listOf(
            Glow(Color(0x2EC98A5E), 0.5f, 1.05f, 1.15f),
            Glow(Color(0x14A9B4C8), 0.1f, 0.02f, 0.95f),
        ),
        fg = Color(0xFFF1EEEA), muted = Color(0xFFB5B0AA),
        accent = Color(0xFFE8C9A6), onAccent = Color(0xFF1A1410),
        videoAccent = Color(0xFFF1D9BC), christRed = Color(0xFFFF6B63),
        surface = Color(0xFF17171B), outline = Color(0x33FFFFFF), panel = Color(0xFF16161A),
        navBg = Color(0xE60B0B0E), navMuted = Color(0xFFC9C4BE),
        fabBg = Color(0xFFE8C9A6), fabFg = Color(0xFF1A1410),
        pastels = listOf(
            Color(0xFFE6CDB4), Color(0xFFCBD3E2), Color(0xFFD9CCE0), Color(0xFFE8C4C0), Color(0xFFE3DDB8),
        ),
        selFill = Color.White.copy(alpha = 0.16f), selBorder = Color.White.copy(alpha = 0.30f),
        selText = Color(0xFFF1EEEA),
    )

    val Midnight = UcdStyle(
        id = "midnight", name = "Medianoche",
        description = "Azul noche profundo con cristal frío y acento celeste.",
        dark = true,
        bg = listOf(Color(0xFF0A1226), Color(0xFF070C1B), Color(0xFF080E20)),
        glows = listOf(
            Glow(Color(0x40305A96), 0.5f, 1.05f, 1.1f),
            Glow(Color(0x334B5FB0), 0.12f, 0.02f, 0.9f),
        ),
        fg = Color(0xFFE8EEFF), muted = Color(0xFF9AA8C8),
        accent = Color(0xFF8FB4FF), onAccent = Color(0xFF07122A),
        videoAccent = Color(0xFFA9C6FF), christRed = Color(0xFFFF7A70),
        surface = Color(0xFF101830), outline = Color(0x33BFD4FF), panel = Color(0xFF0F1730),
        glassTint = Color(0xFFDCE6FF),
        edgeA = Color(0x8CBFD4FF), edgeB = Color(0x0DFFFFFF), edgeC = Color(0x4DBFD4FF),
        navBg = Color(0xE6070C1B), navMuted = Color(0xFFB4C2E2),
        fabBg = Color(0xFF8FB4FF), fabFg = Color(0xFF07122A),
        pastels = listOf(
            Color(0xFFBFD6F5), Color(0xFFC9D2F7), Color(0xFFD3C9F3), Color(0xFFB9E0EE), Color(0xFFDCE6FF),
        ),
        selFill = Color(0xFF8FB4FF).copy(alpha = 0.20f), selBorder = Color(0xFF8FB4FF).copy(alpha = 0.50f),
        selText = Color(0xFFE8EEFF),
    )

    val Sunset = UcdStyle(
        id = "sunset", name = "Atardecer",
        description = "Degradado naranja cálido con cristal esmerilado claro.",
        dark = false,
        bg = listOf(Color(0xFFFFD3B0), Color(0xFFFFA575), Color(0xFFF0825C)),
        glows = listOf(
            Glow(Color(0x66FFFFFF), 0.2f, 0.1f, 0.8f),
            Glow(Color(0x55E8603A), 0.9f, 1.0f, 1.0f),
        ),
        fg = Color(0xFF3A1F14), muted = Color(0xFF7A5646),
        accent = Color(0xFFC63D12), onAccent = Color.White,
        videoAccent = Color(0xFFFFB38A), christRed = Color(0xFFB3261E),
        surface = Color(0xFFFFF1E6), outline = Color(0x55FFFFFF), panel = Color(0xFFFFF1E6),
        glassBase = Color.White.copy(alpha = 0.18f), glassTint = Color.White,
        fillTop = 0.62f, fillBottom = 0.30f,
        edgeA = Color.White, edgeB = Color.Black.copy(alpha = 0.07f), edgeC = Color.White.copy(alpha = 0.80f),
        sheen = 0.40f, glow = 0.95f,
        navBg = Color(0xE03A1F14), navMuted = Color(0xFFF0D2C0),
        fabBg = Color.White, fabFg = Color(0xFF3A1F14),
        pastels = listOf(
            Color(0xFFFFE0C7), Color(0xFFFFD0C2), Color(0xFFF8E7B5), Color(0xFFF5C6D0), Color(0xFFFFEAD6),
        ),
        pastelText = Color(0xFF3A1F14),
        selFill = Color.White.copy(alpha = 0.60f), selBorder = Color.White.copy(alpha = 0.90f),
        selText = Color(0xFF3A1F14),
        corner = 1.2f,
    )

    val Forest = UcdStyle(
        id = "forest", name = "Bosque",
        description = "Verde bosque profundo con cristal de bruma y acento esmeralda.",
        dark = true,
        bg = listOf(Color(0xFF0C1712), Color(0xFF08110D), Color(0xFF0A130E)),
        glows = listOf(
            Glow(Color(0x3329A37A), 0.5f, 1.05f, 1.1f),
            Glow(Color(0x1A8FD0B0), 0.1f, 0.02f, 0.9f),
        ),
        fg = Color(0xFFE9F3EC), muted = Color(0xFF9DB5A7),
        accent = Color(0xFF7DD3A8), onAccent = Color(0xFF06150E),
        videoAccent = Color(0xFF9BE3BF), christRed = Color(0xFFFF7A70),
        surface = Color(0xFF111C16), outline = Color(0x33CFF5DD), panel = Color(0xFF111C16),
        glassBase = Color.Black.copy(alpha = 0.16f), glassTint = Color(0xFFDFFFEA),
        fillTop = 0.13f, fillBottom = 0.035f,
        edgeA = Color(0x8CCFF5DD), edgeB = Color(0x0DFFFFFF), edgeC = Color(0x47CFF5DD),
        navBg = Color(0xE6060E0A), navMuted = Color(0xFFB4CCBE),
        fabBg = Color(0xFF7DD3A8), fabFg = Color(0xFF06150E),
        pastels = listOf(
            Color(0xFFBFE3D2), Color(0xFFCFE8C4), Color(0xFFB7DCD6), Color(0xFFDCEBBF), Color(0xFFC4E1E6),
        ),
        pastelText = Color(0xFF0E2A1D),
        selFill = Color(0xFF7DD3A8).copy(alpha = 0.18f), selBorder = Color(0xFF7DD3A8).copy(alpha = 0.45f),
        selText = Color(0xFFE9F3EC),
    )

    val Parchment = UcdStyle(
        id = "parchment", name = "Pergamino",
        description = "Papel crema, letra con serifa y esquinas sobrias. Ideal para leer la Biblia.",
        dark = false, flat = true,
        bg = listOf(Color(0xFFF4ECD8), Color(0xFFEFE4CA), Color(0xFFEADDBF)),
        glows = listOf(Glow(Color(0x26B58A4A), 0.5f, 1.1f, 1.1f)),
        fg = Color(0xFF2E2418), muted = Color(0xFF76664F),
        accent = Color(0xFF8A4B1F), onAccent = Color(0xFFFFF6E6),
        videoAccent = Color(0xFFE6BE8A), christRed = Color(0xFFB3261E),
        surface = Color(0xFFFAF4E4), outline = Color(0xFFCDBB94), panel = Color(0xFFFAF4E4),
        selFill = Color(0xFF8A4B1F), selBorder = Color.Transparent, selText = Color(0xFFFFF6E6),
        corner = 0.45f, font = FontFamily.Serif,
    )

    val Paper = UcdStyle(
        id = "paper", name = "Blanco",
        description = "Blanco limpio, líneas finas y cero adornos.",
        dark = false, flat = true,
        bg = listOf(Color(0xFFF7F7F8), Color(0xFFF2F2F4), Color(0xFFEDEDF0)),
        fg = Color(0xFF111113), muted = Color(0xFF6B6B73),
        accent = Color(0xFF1F1F23), onAccent = Color.White,
        videoAccent = Color.White, christRed = Color(0xFFC62828),
        surface = Color.White, outline = Color(0xFFD9D9DE), panel = Color.White,
        selFill = Color(0xFF1F1F23), selBorder = Color.Transparent, selText = Color.White,
        corner = 0.6f,
    )

    val Amoled = UcdStyle(
        id = "amoled", name = "Negro puro",
        description = "Negro total con líneas finas. Modo de máximo rendimiento: sin animaciones ni efectos de cristal, ahorra batería en pantallas OLED.",
        dark = true, flat = true,
        bg = listOf(Color.Black, Color.Black),
        fg = Color.White, muted = Color(0xFF9A9AA0),
        accent = Color.White, onAccent = Color.Black,
        videoAccent = Color.White, christRed = Color(0xFFFF5A52),
        surface = Color(0xFF0A0A0A), outline = Color(0xFF2A2A2E), panel = Color(0xFF0A0A0A),
        selFill = Color.White, selBorder = Color.Transparent, selText = Color.Black,
        corner = 0.8f,
    )

    val Neon = UcdStyle(
        id = "neon", name = "Neón",
        description = "Noche violeta con bordes de neón, esquinas rectas y letra de máquina.",
        dark = true, flat = true,
        bg = listOf(Color(0xFF0A0614), Color(0xFF06030F), Color(0xFF080414)),
        glows = listOf(
            Glow(Color(0x40FF2E9A), 0.1f, 1.0f, 0.9f),
            Glow(Color(0x3300E5FF), 0.95f, 0.0f, 0.9f),
        ),
        fg = Color(0xFFEAF6FF), muted = Color(0xFF8FA3C8),
        accent = Color(0xFF00E5FF), onAccent = Color(0xFF001418),
        videoAccent = Color(0xFF00E5FF), christRed = Color(0xFFFF5C8A),
        surface = Color(0xFF0F0A1E), outline = Color(0xCCFF2E9A), panel = Color(0xFF0F0A1E),
        selFill = Color(0xFF00E5FF).copy(alpha = 0.15f), selBorder = Color(0xFF00E5FF),
        selText = Color(0xFF00E5FF),
        corner = 0.3f, font = FontFamily.Monospace,
    )

    /** Cristal en versión oscura: noche azulada con las mismas manchas pastel, más tenues. */
    /** Noche azul violeta con el mismo tipo de fondo que Medianoche y Bosque: degradado + 2 manchas suaves. */
    val CrystalNight = UcdStyle(
        id = "crystal_night", name = "Cristal noche",
        description = "Noche azul violeta con cristal esmerilado y acento amarillo.",
        dark = true,
        bg = listOf(Color(0xFF14172E), Color(0xFF0D1022), Color(0xFF0F1226)),
        glows = listOf(
            Glow(Color(0x40805CF0), 0.5f, 1.05f, 1.1f),
            Glow(Color(0x2EF6A6D2), 0.12f, 0.02f, 0.9f),
        ),
        fg = Color(0xFFEFF2FF), muted = Color(0xFFA9B3D1),
        accent = Color(0xFFFFF48A), onAccent = Color(0xFF1B2240),
        videoAccent = Color(0xFFFFF48A), christRed = Color(0xFFFF8F9E),
        surface = Color(0xFF1B2140), outline = Color(0x40FFFFFF), panel = Color(0xFF1A2038),
        glassBase = Color.Black.copy(alpha = 0.16f), glassTint = Color(0xFFE6E8FF),
        fillTop = 0.13f, fillBottom = 0.035f,
        edgeA = Color(0x8CD8D4FF), edgeB = Color(0x0DFFFFFF), edgeC = Color(0x47D8D4FF),
        navBg = Color(0xE60B0F1E), navMuted = Color(0xFFB4BEDC),
        fabBg = Color.White, fabFg = Color(0xFF1B2240),
        pastels = listOf(
            Color(0xFFBFE3D2), Color(0xFFC9DDF7), Color(0xFFD8CCF3), Color(0xFFF5C6DD), Color(0xFFF4EFC2),
        ),
        selFill = Color.White, selBorder = Color.Transparent, selText = Color(0xFF1B2240),
        corner = 1.1f, titleWeight = FontWeight.SemiBold, titleSize = 26f,
    )

    /** Verde salvia con cristal esmerilado y cápsulas oscuras (segunda referencia). */
    val Sage = UcdStyle(
        id = "sage", name = "Salvia",
        description = "Verde salvia suave con cristal esmerilado, cápsulas oscuras y acento amarillo.",
        dark = true,
        bg = listOf(Color(0xFF739A89), Color(0xFF608675), Color(0xFF517767)),
        glows = listOf(
            Glow(Color(0x40FFFFFF), 0.15f, 0.0f, 0.8f),
            Glow(Color(0x66173128), 0.72f, 0.55f, 0.8f),
            Glow(Color(0x40D8F5E4), 0.2f, 0.9f, 0.7f),
        ),
        fg = Color(0xFFF6FBF7), muted = Color(0xFFD2E4D8),
        accent = Color(0xFFFFF48A), onAccent = Color(0xFF1E2A25),
        videoAccent = Color(0xFFFFF48A), christRed = Color(0xFFFFA89E),
        surface = Color(0xFF3F6455), outline = Color(0x66FFFFFF), panel = Color(0xFF2C473B),
        glassBase = Color.Black.copy(alpha = 0.18f), glassTint = Color.White,
        fillTop = 0.20f, fillBottom = 0.06f,
        edgeA = Color.White.copy(alpha = 0.70f), edgeB = Color.White.copy(alpha = 0.08f),
        edgeC = Color.White.copy(alpha = 0.40f),
        sheen = 0.14f, glow = 0.60f, rim = 0.20f,
        navBg = Color(0xE61E2A25), navMuted = Color(0xFFCADBD0),
        fabBg = Color.White, fabFg = Color(0xFF1E2A25),
        pastels = listOf(
            Color(0xFFBFE3D2), Color(0xFFC9DDF7), Color(0xFFD8CCF3), Color(0xFFF5C6DD), Color(0xFFF4EFC2),
        ),
        selFill = Color.White, selBorder = Color.Transparent, selText = Color(0xFF1E2A25),
        corner = 1.1f, titleWeight = FontWeight.SemiBold, titleSize = 26f,
        twin = "sage_light",
    )

    /** Salvia en versión clara: verde suave y luminoso con texto oscuro. */
    val SageLight = UcdStyle(
        id = "sage_light", name = "Salvia clara",
        description = "La versión clara de Salvia: verde menta luminoso con cristal blanco.",
        dark = false,
        bg = listOf(Color(0xFFDCEBE2), Color(0xFFC3D9CC), Color(0xFFAFC9BA)),
        glows = listOf(
            Glow(Color(0x73FFFFFF), 0.2f, 0.0f, 0.8f),
            Glow(Color(0x66BDE8CF), 0.8f, 0.3f, 0.7f),
            Glow(Color(0x40FFF1B0), 0.9f, 0.95f, 0.6f),
            Glow(Color(0x2E2F5545), 0.7f, 0.6f, 0.8f),
        ),
        fg = Color(0xFF1C2B24), muted = Color(0xFF54695E),
        accent = Color(0xFF2E6A52), onAccent = Color.White,
        videoAccent = Color(0xFFFFF48A), christRed = Color(0xFFB3261E),
        surface = Color(0xFFF1F7F3), outline = Color(0x99FFFFFF), panel = Color(0xFFE8F1EB),
        glassBase = Color.White.copy(alpha = 0.10f), glassTint = Color.White,
        fillTop = 0.58f, fillBottom = 0.22f,
        edgeA = Color.White, edgeB = Color.White.copy(alpha = 0.18f), edgeC = Color.White.copy(alpha = 0.85f),
        sheen = 0.36f, glow = 0.85f, rim = 0.28f,
        navBg = Color(0xE0233229), navMuted = Color(0xFFCCDCD2),
        fabBg = Color.White, fabFg = Color(0xFF1C2B24),
        pastels = listOf(
            Color(0xFFBFE3D2), Color(0xFFC9DDF7), Color(0xFFD8CCF3), Color(0xFFF5C6DD), Color(0xFFF4EFC2),
        ),
        selFill = Color.White, selBorder = Color.Transparent, selText = Color(0xFF1C2B24),
        corner = 1.1f, titleWeight = FontWeight.SemiBold, titleSize = 26f,
        twin = "sage",
    )

    val all: List<UcdStyle> = listOf(Midnight, CrystalNight, Sage, SageLight, Graphite, Sunset, Forest, Parchment, Paper, Amoled, Neon)

    fun byId(id: String?): UcdStyle = all.firstOrNull { it.id == id } ?: Midnight

    /** Id del tema al que se pasa con el interruptor de modo oscuro. */
    fun toggleDark(cur: UcdStyle): String = cur.twin ?: if (cur.dark) Paper.id else Midnight.id
}

/** Tema activo. Es "estático" para que, al cambiarlo, se redibuje toda la pantalla. */
val LocalUcdStyle = staticCompositionLocalOf { Themes.Midnight }
