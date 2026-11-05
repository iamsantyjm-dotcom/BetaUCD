package com.ucd.player.data

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Operaciones sobre los videos de la biblioteca (renombrar, mover, eliminar).
 * Si Android pide permiso para tocar un archivo que no creó esta app, devuelve NeedsGrant con la
 * ventana del sistema que hay que mostrar; al aceptar, se repite la operación.
 */
object VideoOps {
    sealed class Result {
        object Ok : Result()
        class NeedsGrant(val sender: IntentSender) : Result()
        class Error(val message: String) : Result()
    }

    /** Carpetas donde Android permite guardar videos. */
    val ROOTS = listOf("Movies/", "DCIM/", "Pictures/")

    private fun grantFor(e: SecurityException, uri: Uri, ctx: Context): Result {
        if (Build.VERSION.SDK_INT >= 30) {
            return Result.NeedsGrant(MediaStore.createWriteRequest(ctx.contentResolver, listOf(uri)).intentSender)
        }
        if (Build.VERSION.SDK_INT == 29 && e is RecoverableSecurityException) {
            return Result.NeedsGrant(e.userAction.actionIntent.intentSender)
        }
        return Result.Error("Android no permite modificar este archivo.")
    }

    private fun queryName(ctx: Context, uri: Uri): String? =
        ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    fun rename(ctx: Context, uri: Uri, newBase: String): Result {
        val clean = newBase.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().trimEnd('.').trim()
        if (clean.isEmpty()) return Result.Error("El nombre no es válido.")
        val old = queryName(ctx, uri) ?: return Result.Error("No se encontró el archivo.")
        val ext = old.substringAfterLast('.', "")
        val name = if (ext.isEmpty()) clean else "$clean.$ext"
        if (name == old) return Result.Ok
        return try {
            val v = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }
            if (ctx.contentResolver.update(uri, v, null, null) > 0) Result.Ok
            else Result.Error("No se pudo renombrar el video.")
        } catch (e: SecurityException) {
            grantFor(e, uri, ctx)
        } catch (e: Exception) {
            Result.Error("No se pudo renombrar. Quizá ya existe un video con ese nombre en la carpeta.")
        }
    }

    /** `relativePath` como "Movies/UCD/". */
    fun move(ctx: Context, uri: Uri, relativePath: String): Result {
        if (Build.VERSION.SDK_INT < 29) return Result.Error("Mover videos necesita Android 10 o superior.")
        val rel = relativePath.trim().trim('/') + "/"
        return try {
            val v = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, rel) }
            if (ctx.contentResolver.update(uri, v, null, null) > 0) Result.Ok
            else Result.Error("No se pudo mover el video.")
        } catch (e: SecurityException) {
            grantFor(e, uri, ctx)
        } catch (e: IllegalArgumentException) {
            Result.Error("Android no permite guardar videos ahí. Usa una carpeta dentro de Movies, DCIM o Pictures.")
        } catch (e: Exception) {
            Result.Error("No se pudo mover el video.")
        }
    }

    fun delete(ctx: Context, uris: List<Uri>): Result {
        return try {
            var n = 0
            uris.forEach { n += ctx.contentResolver.delete(it, null, null) }
            if (n > 0) Result.Ok else Result.Error("No se pudo eliminar el video.")
        } catch (e: SecurityException) {
            when {
                Build.VERSION.SDK_INT >= 30 ->
                    Result.NeedsGrant(MediaStore.createDeleteRequest(ctx.contentResolver, uris).intentSender)
                Build.VERSION.SDK_INT == 29 && e is RecoverableSecurityException ->
                    Result.NeedsGrant(e.userAction.actionIntent.intentSender)
                else -> Result.Error("Android no permite eliminar este archivo.")
            }
        } catch (e: Exception) {
            Result.Error("No se pudo eliminar el video.")
        }
    }
}
