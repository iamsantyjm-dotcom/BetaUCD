package com.ucd.player.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/** Guarda el archivo ya descargado en la carpeta elegida (o en Películas/UCD si no se eligió ninguna). */
object Saver {
    private val AUDIO_EXT = setOf("m4a", "mp3", "opus", "ogg", "aac", "flac", "wav")

    fun isAudio(file: File) = file.extension.lowercase() in AUDIO_EXT

    /** Texto corto para mostrar la carpeta. */
    fun label(tree: String?): String {
        if (tree == null) return "Películas/UCD (predeterminada)"
        val id = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(tree)) }.getOrNull()
            ?: return "Carpeta elegida"
        val vol = id.substringBefore(':')
        val path = id.substringAfter(':', "")
        return when {
            path.isNotBlank() -> path
            vol == "primary" -> "Almacenamiento interno"
            else -> "Tarjeta SD / otro volumen"
        }
    }

    private fun treeUsable(ctx: Context, tree: String): Boolean {
        val uri = Uri.parse(tree)
        return ctx.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    }

    private fun mimeOf(ext: String): String = when (ext.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "opus" -> "audio/opus"
        "ogg" -> "audio/ogg"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }

    private fun copyTo(ctx: Context, src: File, dest: Uri) {
        val out = ctx.contentResolver.openOutputStream(dest, "w")
            ?: throw IOException("No se pudo abrir el archivo de destino")
        out.use { o -> src.inputStream().use { i -> i.copyTo(o, 256 * 1024) } }
    }

    /** Copia [file] al destino y devuelve el lugar donde quedó (para mostrarlo). */
    fun save(ctx: Context, file: File, tree: String?): String {
        val name = file.name
        val mime = mimeOf(file.extension)

        // 1) Carpeta elegida por el usuario (Storage Access Framework)
        if (tree != null && treeUsable(ctx, tree)) {
            val treeUri = Uri.parse(tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            val doc = DocumentsContract.createDocument(ctx.contentResolver, parent, mime, name)
                ?: throw IOException("No se pudo crear el archivo en la carpeta elegida")
            try {
                copyTo(ctx, file, doc)
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, doc) }
                throw e
            }
            return label(tree)
        }

        // 2) Android 10+: MediaStore (Películas/UCD o Música/UCD), sin permisos extra
        if (Build.VERSION.SDK_INT >= 29) {
            val audio = isAudio(file)
            val collection = if (audio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val rel = if (audio) "Music/UCD" else "Movies/UCD"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(collection, values)
                ?: throw IOException("No se pudo crear el archivo en $rel")
            try {
                copyTo(ctx, file, uri)
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                ctx.contentResolver.update(uri, done, null, null)
            } catch (e: Exception) {
                runCatching { ctx.contentResolver.delete(uri, null, null) }
                throw e
            }
            return rel
        }

        // 3) Android 8–9: carpeta propia de la app en el almacenamiento externo
        val base = ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: ctx.filesDir
        val dir = File(base, "UCD").also { it.mkdirs() }
        var dest = File(dir, name)
        var n = 1
        while (dest.exists()) { dest = File(dir, file.nameWithoutExtension + " (" + n + ")." + file.extension); n++ }
        file.copyTo(dest)
        return dest.parentFile?.absolutePath ?: dir.absolutePath
    }
}
