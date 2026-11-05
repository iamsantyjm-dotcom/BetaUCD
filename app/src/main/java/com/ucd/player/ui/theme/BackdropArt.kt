package com.ucd.player.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color

/**
 * Fondo "con vida": de cada miniatura se saca UN color dominante (vivo, no apagado) y el fondo
 * pinta con ellos manchas de luz suaves (degradados radiales). Al ser degradados puros no hay
 * píxeles ni cuadritos, y no cuesta rendimiento al desplazarte.
 */
object BackdropArt {
    /** Posición (x, y) y radio de cada mancha, como fracción del ancho/alto de la pantalla. */
    val spots = listOf(
        Triple(0.90f, 0.18f, 0.95f),
        Triple(0.05f, 0.58f, 1.00f),
        Triple(0.85f, 0.95f, 0.90f),
    )

    /** Color dominante de cada miniatura (hasta 3). */
    fun colors(thumbs: List<Bitmap>): List<Color> = thumbs.take(spots.size).mapNotNull { runCatching { dominant(it) }.getOrNull() }

    private fun dominant(original: Bitmap): Color? {
        // Copia de software (algunas miniaturas vienen como bitmap de hardware) y muy pequeña
        val soft = original.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        val s = Bitmap.createScaledBitmap(soft, 16, 16, true)
        val hsv = FloatArray(3)
        var r = 0.0; var g = 0.0; var b = 0.0; var total = 0.0
        for (y in 0 until s.height) for (x in 0 until s.width) {
            val c = s.getPixel(x, y)
            android.graphics.Color.colorToHSV(c, hsv)
            // Pesa más los colores vivos y claros que el negro o el gris
            val wgt = (hsv[1] * hsv[2]).toDouble() + 0.02
            r += android.graphics.Color.red(c) * wgt
            g += android.graphics.Color.green(c) * wgt
            b += android.graphics.Color.blue(c) * wgt
            total += wgt
        }
        if (total <= 0.0) return null
        val avg = android.graphics.Color.rgb((r / total).toInt(), (g / total).toInt(), (b / total).toInt())
        android.graphics.Color.colorToHSV(avg, hsv)
        // Más vivo y con brillo medio: queda bien tanto en temas claros como oscuros
        hsv[1] = (hsv[1] * 1.15f).coerceIn(0.35f, 0.95f)
        hsv[2] = hsv[2].coerceIn(0.55f, 0.95f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}
