package com.ucd.player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Arrays

/**
 * Biblia Reina Valera 1960, guardada en un único archivo de texto dentro de la app
 * (assets/biblia.txt). Se lee una sola vez, en segundo plano, la primera vez que se necesita.
 *
 * Formato del archivo: "@clave" empieza un libro, "#" empieza un capítulo y cada línea
 * siguiente es un versículo. Dentro de un versículo: "¶" = salto de línea (poesía) y
 * _texto_ = palabras de Cristo.
 */
object Bible {

    class B(val key: String, val name: String, val abbr: String, val nt: Boolean, val chapters: Int) {
        internal val nameKey = norm(name).replace(" ", "")
        internal val abbrKey = norm(abbr).replace(" ", "")
        /** Nombre sin el número inicial ("1 Juan" → "juan"). */
        internal val coreKey = nameKey.dropWhile { it.isDigit() }
    }

    val books: List<B> = listOf(
        B("genesis", "Génesis", "Gn", false, 50),
        B("exodo", "Éxodo", "Ex", false, 40),
        B("levitico", "Levítico", "Lv", false, 27),
        B("numeros", "Números", "Nm", false, 36),
        B("deuteronomio", "Deuteronomio", "Dt", false, 34),
        B("josue", "Josué", "Jos", false, 24),
        B("jueces", "Jueces", "Jue", false, 21),
        B("rut", "Rut", "Rt", false, 4),
        B("1_samuel", "1 Samuel", "1 S", false, 31),
        B("2_samuel", "2 Samuel", "2 S", false, 24),
        B("1_reyes", "1 Reyes", "1 R", false, 22),
        B("2_reyes", "2 Reyes", "2 R", false, 25),
        B("1_cronicas", "1 Crónicas", "1 Cr", false, 29),
        B("2_cronicas", "2 Crónicas", "2 Cr", false, 36),
        B("esdras", "Esdras", "Esd", false, 10),
        B("nehemias", "Nehemías", "Neh", false, 13),
        B("ester", "Ester", "Est", false, 10),
        B("job", "Job", "Job", false, 42),
        B("salmos", "Salmos", "Sal", false, 150),
        B("proverbios", "Proverbios", "Pr", false, 31),
        B("eclesiastes", "Eclesiastés", "Ec", false, 12),
        B("cantares", "Cantares", "Cnt", false, 8),
        B("isaias", "Isaías", "Is", false, 66),
        B("jeremias", "Jeremías", "Jer", false, 52),
        B("lamentaciones", "Lamentaciones", "Lm", false, 5),
        B("ezequiel", "Ezequiel", "Ez", false, 48),
        B("daniel", "Daniel", "Dn", false, 12),
        B("oseas", "Oseas", "Os", false, 14),
        B("joel", "Joel", "Jl", false, 3),
        B("amos", "Amós", "Am", false, 9),
        B("abdias", "Abdías", "Abd", false, 1),
        B("jonas", "Jonás", "Jon", false, 4),
        B("miqueas", "Miqueas", "Miq", false, 7),
        B("nahum", "Nahúm", "Nah", false, 3),
        B("habacuc", "Habacuc", "Hab", false, 3),
        B("sofonias", "Sofonías", "Sof", false, 3),
        B("hageo", "Hageo", "Hg", false, 2),
        B("zacarias", "Zacarías", "Zac", false, 14),
        B("malaquias", "Malaquías", "Mal", false, 4),
        B("mateo", "Mateo", "Mt", true, 28),
        B("marcos", "Marcos", "Mc", true, 16),
        B("lucas", "Lucas", "Lc", true, 24),
        B("juan", "Juan", "Jn", true, 21),
        B("hechos", "Hechos", "Hch", true, 28),
        B("romanos", "Romanos", "Ro", true, 16),
        B("1_corintios", "1 Corintios", "1 Co", true, 16),
        B("2_corintios", "2 Corintios", "2 Co", true, 13),
        B("galatas", "Gálatas", "Gl", true, 6),
        B("efesios", "Efesios", "Ef", true, 6),
        B("filipenses", "Filipenses", "Flp", true, 4),
        B("colosenses", "Colosenses", "Col", true, 4),
        B("1_tesalonicenses", "1 Tesalonicenses", "1 Ts", true, 5),
        B("2_tesalonicenses", "2 Tesalonicenses", "2 Ts", true, 3),
        B("1_timoteo", "1 Timoteo", "1 Ti", true, 6),
        B("2_timoteo", "2 Timoteo", "2 Ti", true, 4),
        B("tito", "Tito", "Tit", true, 3),
        B("filemon", "Filemón", "Flm", true, 1),
        B("hebreos", "Hebreos", "Heb", true, 13),
        B("santiago", "Santiago", "Stg", true, 5),
        B("1_pedro", "1 Pedro", "1 P", true, 5),
        B("2_pedro", "2 Pedro", "2 P", true, 3),
        B("1_juan", "1 Juan", "1 Jn", true, 5),
        B("2_juan", "2 Juan", "2 Jn", true, 1),
        B("3_juan", "3 Juan", "3 Jn", true, 1),
        B("judas", "Judas", "Jud", true, 1),
        B("apocalipsis", "Apocalipsis", "Ap", true, 22),
    )

    /** Resultado de una búsqueda. */
    class SearchResult(
        /** [libro, capítulo, versículo] (versículo 0 = solo capítulo), si se escribió una cita. */
        val ref: IntArray?,
        /** Libros cuyo nombre coincide. */
        val books: List<Int>,
        /** Posiciones (índice global) de los versículos encontrados. */
        val hits: IntArray,
        /** Total de coincidencias (hits puede ser solo una parte). */
        val total: Int,
    )

    private const val MAX_HITS = 200

    // verses[libro][capítulo][versículo]
    @Volatile private var verses: Array<Array<Array<String>>>? = null
    // Índice global: chapStart[g] = posición del primer versículo del capítulo global g
    private var chapStart = IntArray(0)
    private var bookFirstChap = IntArray(0)
    var totalVerses = 0
        private set
    @Volatile private var searchIndex: Array<String>? = null
    private val lock = Mutex()

    val isLoaded: Boolean get() = verses != null

    suspend fun ensureLoaded(ctx: Context) {
        if (verses != null) return
        lock.withLock {
            if (verses != null) return
            val app = ctx.applicationContext
            val parsed = withContext(Dispatchers.IO) { parse(app) }
            verses = parsed
        }
    }

    private fun parse(ctx: Context): Array<Array<Array<String>>> {
        val bookList = ArrayList<ArrayList<ArrayList<String>>>(books.size)
        ctx.assets.open("biblia.txt").bufferedReader(Charsets.UTF_8).useLines { seq ->
            var chap: ArrayList<String>? = null
            var book: ArrayList<ArrayList<String>>? = null
            for (line in seq) {
                if (line.isEmpty()) continue
                when (line[0]) {
                    '@' -> { book = ArrayList(); bookList.add(book); chap = null }
                    '#' -> { chap = ArrayList(); book!!.add(chap) }
                    else -> chap!!.add(if (line.indexOf('¶') >= 0) line.replace('¶', '\n') else line)
                }
            }
        }
        val arr = Array(bookList.size) { b -> Array(bookList[b].size) { c -> bookList[b][c].toTypedArray() } }
        // Índice global
        var chapters = 0
        for (b in arr) chapters += b.size
        val cs = IntArray(chapters + 1)
        val bf = IntArray(arr.size + 1)
        var g = 0; var flat = 0
        for (b in arr.indices) {
            bf[b] = g
            for (c in arr[b].indices) { cs[g++] = flat; flat += arr[b][c].size }
        }
        cs[g] = flat; bf[arr.size] = g
        chapStart = cs; bookFirstChap = bf; totalVerses = flat
        return arr
    }

    // ---------- Acceso ----------
    fun chapterCount(book: Int): Int = verses?.get(book)?.size ?: books[book].chapters
    fun chapter(book: Int, chapter0: Int): Array<String> = verses!![book][chapter0]
    fun verseCount(book: Int, chapter0: Int): Int = verses!![book][chapter0].size

    /** Posición global de un versículo (todo base 0). */
    fun flatIndex(book: Int, chapter0: Int, verse0: Int): Int = chapStart[bookFirstChap[book] + chapter0] + verse0

    /** Devuelve [libro, capítulo, versículo] (base 0) de una posición global. */
    fun locate(flat: Int): IntArray {
        val f = flat.coerceIn(0, totalVerses - 1)
        val gi = Arrays.binarySearch(chapStart, 0, chapStart.size - 1, f)
        val g = if (gi >= 0) gi else -gi - 2
        val bi = Arrays.binarySearch(bookFirstChap, 0, bookFirstChap.size - 1, g)
        val b = if (bi >= 0) bi else -bi - 2
        return intArrayOf(b, g - bookFirstChap[b], f - chapStart[g])
    }

    fun verseAt(flat: Int): String {
        val l = locate(flat)
        return verses!![l[0]][l[1]][l[2]]
    }

    /** Ej.: "Juan 3:16" */
    fun label(flat: Int): String {
        val l = locate(flat)
        return books[l[0]].name + " " + (l[1] + 1) + ":" + (l[2] + 1)
    }

    // ---------- Búsqueda ----------
    private val refRegex = Regex("^(\\d?\\s*[a-z][a-z ]*?)\\s*(?:(\\d{1,3})(?:\\s*[:.,]?\\s*(\\d{1,3}))?)?$")
    private val spaces = Regex("\\s+")

    private fun matchBooks(part: String): List<Int> {
        val p = part.replace(" ", "")
        if (p.isEmpty()) return emptyList()
        val exact = ArrayList<Int>()
        val prefix = ArrayList<Int>()
        for (i in books.indices) {
            val b = books[i]
            when {
                b.nameKey == p || b.abbrKey == p -> exact.add(i)
                b.nameKey.startsWith(p) || b.abbrKey.startsWith(p) ||
                    (!p[0].isDigit() && b.coreKey.startsWith(p)) -> prefix.add(i)
            }
        }
        return exact + prefix
    }

    suspend fun search(ctx: Context, query: String): SearchResult = withContext(Dispatchers.Default) {
        ensureLoaded(ctx)
        val nq = norm(query).trim().replace(spaces, " ")
        if (nq.isEmpty()) return@withContext SearchResult(null, emptyList(), IntArray(0), 0)

        // 1) ¿Es una cita ("jn 3:16", "1 juan 2") o un nombre de libro?
        var ref: IntArray? = null
        var found: List<Int> = emptyList()
        val m = refRegex.find(nq)
        if (m != null) {
            found = matchBooks(m.groupValues[1].trim())
            val chap = m.groupValues[2].toIntOrNull()
            val ver = m.groupValues[3].toIntOrNull()
            if (found.isNotEmpty() && chap != null) {
                val b = found[0]
                if (chap in 1..chapterCount(b)) {
                    val v = if (ver != null && ver in 1..verseCount(b, chap - 1)) ver else 0
                    ref = intArrayOf(b, chap, v)
                }
                found = emptyList()
            }
        }
        if (ref != null) return@withContext SearchResult(ref, emptyList(), IntArray(0), 0)

        // 2) Búsqueda por texto (todas las palabras, sin importar acentos ni mayúsculas)
        if (nq.length < 2) return@withContext SearchResult(null, found, IntArray(0), 0)
        val words = nq.split(' ').filter { it.isNotEmpty() }
        val idx = searchIndex ?: synchronized(this@Bible) {
            searchIndex ?: buildSearchIndex().also { searchIndex = it }
        }
        val out = IntArray(MAX_HITS)
        var n = 0; var total = 0
        for (i in idx.indices) {
            val t = idx[i]
            var ok = true
            for (w in words) if (!t.contains(w)) { ok = false; break }
            if (ok) { if (n < MAX_HITS) out[n++] = i; total++ }
        }
        SearchResult(null, found, out.copyOf(n), total)
    }

    private fun buildSearchIndex(): Array<String> {
        val v = verses!!
        val out = arrayOfNulls<String>(totalVerses)
        var i = 0
        for (b in v) for (c in b) for (t in c) out[i++] = norm(t)
        @Suppress("UNCHECKED_CAST")
        return out as Array<String>
    }
}

/** Minúsculas, sin acentos ni ñ, sin marcas de palabras de Cristo y con saltos de línea como espacio. */
fun norm(s: String): String {
    val sb = StringBuilder(s.length)
    for (ch in s) {
        when (val c = ch.lowercaseChar()) {
            '_' -> {}
            '\n' -> sb.append(' ')
            'á', 'à', 'ä', 'â' -> sb.append('a')
            'é', 'è', 'ë', 'ê' -> sb.append('e')
            'í', 'ì', 'ï', 'î' -> sb.append('i')
            'ó', 'ò', 'ö', 'ô' -> sb.append('o')
            'ú', 'ù', 'ü', 'û' -> sb.append('u')
            'ñ' -> sb.append('n')
            else -> sb.append(c)
        }
    }
    return sb.toString()
}
