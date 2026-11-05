package com.ucd.player.download

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * "Índice al principio" (faststart) para MP4/M4A: si el índice (moov) está al final del archivo,
 * lo pasa al inicio sin recomprimir nada. Así el video abre al instante y se puede saltar de inmediato.
 * Si algo sale raro, el archivo original se deja intacto.
 */
object Faststart {
    private val CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl")

    /** true = el archivo ya tiene (o ahora tiene) el índice al principio. */
    fun ensure(file: File): Boolean = try { run(file) } catch (e: Throwable) { false }

    private class Atom(val start: Long, val size: Long, val type: String, val header: Int)

    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun u64(b: ByteArray, o: Int): Long = (u32(b, o) shl 32) or u32(b, o + 4)

    private fun put32(b: ByteArray, o: Int, v: Long) {
        b[o] = (v shr 24).toByte(); b[o + 1] = (v shr 16).toByte()
        b[o + 2] = (v shr 8).toByte(); b[o + 3] = v.toByte()
    }

    private fun put64(b: ByteArray, o: Int, v: Long) {
        put32(b, o, v ushr 32); put32(b, o + 4, v and 0xFFFFFFFFL)
    }

    private fun topLevel(raf: RandomAccessFile, len: Long): List<Atom> {
        val list = ArrayList<Atom>()
        val h = ByteArray(16)
        var pos = 0L
        while (pos + 8 <= len) {
            raf.seek(pos)
            raf.readFully(h, 0, 8)
            var size = u32(h, 0)
            val type = String(h, 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) {
                raf.readFully(h, 8, 8)
                size = u64(h, 8)
                header = 16
            } else if (size == 0L) {
                size = len - pos
            }
            if (size < header || pos + size > len) break
            list += Atom(pos, size, type, header)
            pos += size
        }
        return list
    }

    /** Suma [delta] a los desplazamientos de datos (stco/co64) que apuntan a [lo, hi). */
    private fun patch(b: ByteArray, from: Int, to: Int, lo: Long, hi: Long, delta: Long): Boolean {
        var p = from
        while (p + 8 <= to) {
            val size = u32(b, p).toInt()
            if (size < 8 || p + size > to) return false
            val type = String(b, p + 4, 4, Charsets.ISO_8859_1)
            if (type in CONTAINERS) {
                if (!patch(b, p + 8, p + size, lo, hi, delta)) return false
            } else if (type == "stco") {
                val n = u32(b, p + 12).toInt()
                if (n < 0 || p + 16 + n.toLong() * 4 > p + size) return false
                var q = p + 16
                for (i in 0 until n) {
                    val o = u32(b, q)
                    if (o >= lo && o < hi) {
                        val v = o + delta
                        if (v > 0xFFFFFFFFL) return false
                        put32(b, q, v)
                    }
                    q += 4
                }
            } else if (type == "co64") {
                val n = u32(b, p + 12).toInt()
                if (n < 0 || p + 16 + n.toLong() * 8 > p + size) return false
                var q = p + 16
                for (i in 0 until n) {
                    val o = u64(b, q)
                    if (o >= lo && o < hi) put64(b, q, o + delta)
                    q += 8
                }
            }
            p += size
        }
        return true
    }

    private fun copyRange(raf: RandomAccessFile, from: Long, to: Long, out: FileOutputStream) {
        val buf = ByteArray(256 * 1024)
        var pos = from
        raf.seek(from)
        while (pos < to) {
            val n = raf.read(buf, 0, minOf(buf.size.toLong(), to - pos).toInt())
            if (n <= 0) throw java.io.IOException("Fin de archivo inesperado")
            out.write(buf, 0, n)
            pos += n
        }
    }

    private fun run(file: File): Boolean {
        val len = file.length()
        val tmp = File(file.parentFile, file.name + ".fs")
        try {
            RandomAccessFile(file, "r").use { raf ->
                val top = topLevel(raf, len)
                val moov = top.firstOrNull { it.type == "moov" } ?: return false
                val mdat = top.firstOrNull { it.type == "mdat" } ?: return false
                if (moov.start < mdat.start) return true          // ya tiene el índice al principio
                if (moov.size > 512L * 1024 * 1024) return false
                val bytes = ByteArray(moov.size.toInt())
                raf.seek(moov.start)
                raf.readFully(bytes)
                // Todo lo que está entre el inicio del mdat y el moov se corre moov.size bytes hacia adelante
                if (!patch(bytes, moov.header, bytes.size, mdat.start, moov.start, moov.size)) return false
                FileOutputStream(tmp).use { out ->
                    copyRange(raf, 0, mdat.start, out)
                    out.write(bytes)
                    copyRange(raf, mdat.start, moov.start, out)
                    copyRange(raf, moov.start + moov.size, len, out)
                }
            }
            if (tmp.length() != len) return false
            return tmp.renameTo(file)   // en Linux reemplaza el original de forma atómica
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
