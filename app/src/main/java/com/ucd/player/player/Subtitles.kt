package com.ucd.player.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Nombre visible de un archivo (sirve para videos abiertos desde otras apps). */
fun displayNameOf(ctx: Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull() ?: uri.lastPathSegment
