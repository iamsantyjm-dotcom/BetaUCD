package com.ucd.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.ucd.player.data.UcdPrefs
import com.ucd.player.ui.UcdRoot
import com.ucd.player.ui.theme.UcdTheme

class MainActivity : ComponentActivity() {
    // Sube cada vez que la app vuelve a primer plano → la biblioteca se vuelve a escanear
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Barras del sistema transparentes; el color de sus iconos lo ajusta el tema elegido
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val db = (application as UcdApp).db
        setContent {
            var themeId by remember { mutableStateOf(UcdPrefs(this@MainActivity).themeId) }
            UcdTheme(themeId = themeId, applySystemBars = true) {
                UcdRoot(db, resumeTick, themeId) { themeId = it }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}
