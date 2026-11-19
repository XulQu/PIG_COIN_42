package com.bootybay.hallowmiss.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

object Status {
    const val PENDING = "PENDING"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"
    const val POSTING = "POSTING"
    const val POSTED = "POSTED"
    const val FAILED = "FAILED"
}

data class Design(
    val id: Long, val created: Long, val phrase: String, val title: String, val tags: String,
    val motifH: String, val motifX: String, val palette: String, val credits: String,
    val png: String, val thumb: String, val svg: String, val status: String, val feedback: String,
    val fileUrl: String, val pfProduct: Long, val mockup: String, val listingId: Long,
    val imagesDone: Boolean, val error: String, val postedAt: Long,
)

data class Sale(
    val txnId: Long, val receiptId: Long, val listingId: Long, val designId: Long, val title: String,
    val amount: Double, val fees: Double, val cost: Double, val currency: String, val ts: Long,
    val pfOrder: String, val country: String,
)

data class ResearchItem(val ts: Long, val source: String, val query: String, val title: String, val price: Double, val favs: Int)
data class Suggestion(val id: Long, val ts: Long, val text: String, val active: Boolean)
data class LogLine(val ts: Long, val msg: String)
class MotifStat { var made = 0; var approved = 0; var rejected = 0; var sold = 0 }

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "bootybay.db", null, 1) {

    companion object {
        @Volatile private var inst: Db? = null
        fun get(ctx: Context): Db = inst ?: synchronized(this) { inst ?: Db(ctx.applicationContext).also { inst = it } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE designs(id INTEGER PRIMARY KEY AUTOINCREMENT, created INTEGER, phrase TEXT, title TEXT,
            tags TEXT, motif_h TEXT, motif_x TEXT, palette TEXT, credits TEXT, png TEXT, thumb TEXT, svg TEXT, status TEXT,
            feedback TEXT DEFAULT '', file_url TEXT DEFAULT '', pf_product INTEGER DEFAULT 0, mockup TEXT DEFAULT '',
            listing_id INTEGER DEFAULT 0, images_done INTEGER DEFAULT 0, error TEXT DEFAULT '', posted_at INTEGER DEFAULT 0)""")
        db.execSQL("CREATE INDEX designs_status ON designs(status)")
        db.execSQL("""CREATE TABLE sales(txn_id INTEGER PRIMARY KEY, receipt_id INTEGER, listing_id INTEGER, design_id INTEGER,
            title TEXT, amount REAL, fees REAL, cost REAL DEFAULT 0, currency TEXT, ts INTEGER, pf_order TEXT DEFAULT '', country TEXT)""")
        db.execSQL("CREATE TABLE research(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, source TEXT, query TEXT, title TEXT, price REAL, favs INTEGER)")
        db.execSQL("CREATE TABLE suggestions(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, text TEXT, active INTEGER DEFAULT 1)")
        db.execSQL("CREATE TABLE used_sources(title TEXT PRIMARY KEY)")
        db.execSQL("CREATE TABLE log(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, msg TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun Cursor.s(c: String): String = getString(getColumnIndexOrThrow(c)) ?: ""
    private fun Cursor.l(c: String): Long = getLong(getColumnIndexOrThrow(c))
    private fun Cursor.d(c: String): Double = getDouble(getColumnIndexOrThrow(c))

    private fun <T> query(sql: String, args: Array<String> = emptyArray(), map: (Cursor) -> T): List<T> {
        val out = ArrayList<T>()
        readableDatabase.rawQuery(sql, args).use { c -> while (c.moveToNext()) out.add(map(c)) }
        return out
    }

    // ---------- designs ----------
    private fun toDesign(c: Cursor) = Design(
        c.l("id"), c.l("created"), c.s("phrase"), c.s("title"), c.s("tags"), c.s("motif_h"), c.s("motif_x"),
        c.s("palette"), c.s("credits"), c.s("png"), c.s("thumb"), c.s("svg"), c.s("status"), c.s("feedback"),
        c.s("file_url"), c.l("pf_product"), c.s("mockup"), c.l("listing_id"), c.l("images_done") == 1L,
        c.s("error"), c.l("posted_at"),
    )

    fun insertDesign(cv: ContentValues): Long = writableDatabase.insert("designs", null, cv)

    fun designs(statuses: List<String>, limit: Int = 300): List<Design> {
        val marks = statuses.joinToString(",") { "?" }
        return query("SELECT * FROM designs WHERE status IN ($marks) ORDER BY id DESC LIMIT $limit", statuses.toTypedArray(), ::toDesign)
    }

    fun allDesigns(): List<Design> = query("SELECT * FROM designs ORDER BY id DESC", map = ::toDesign)
    fun design(id: Long): Design? = query("SELECT * FROM designs WHERE id=?", arrayOf(id.toString()), ::toDesign).firstOrNull()
    fun designByListing(listingId: Long): Design? =
        query("SELECT * FROM designs WHERE listing_id=?", arrayOf(listingId.toString()), ::toDesign).firstOrNull()

    fun update(id: Long, block: ContentValues.() -> Unit) {
        val cv = ContentValues().apply(block)
        writableDatabase.update("designs", cv, "id=?", arrayOf(id.toString()))
    }

    fun setStatus(id: Long, status: String, feedback: String? = null, error: String? = null) = update(id) {
        put("status", status)
        if (feedback != null) put("feedback", feedback)
        if (error != null) put("error", error)
    }

    fun counts(): Map<String, Int> =
        query("SELECT status, COUNT(*) n FROM designs GROUP BY status") { it.s("status") to it.l("n").toInt() }.toMap()

    fun recentCombos(): Set<String> =
        query("SELECT phrase, motif_h, motif_x FROM designs ORDER BY id DESC LIMIT 200") {
            it.s("phrase") + "|" + it.s("motif_h") + "|" + it.s("motif_x")
        }.toSet()

    fun motifStats(): Map<String, MotifStat> {
        val m = HashMap<String, MotifStat>()
        val soldByDesign = query("SELECT design_id, COUNT(*) n FROM sales GROUP BY design_id") { it.l("design_id") to it.l("n").toInt() }.toMap()
        query("SELECT id, motif_h, motif_x, status FROM designs") { Triple(it.l("id"), listOf(it.s("motif_h"), it.s("motif_x")), it.s("status")) }
            .forEach { (id, motifs, status) ->
                for (name in motifs) {
                    val s = m.getOrPut(name) { MotifStat() }
                    s.made++
                    when (status) {
                        Status.REJECTED -> s.rejected++
                        Status.PENDING -> {}
                        else -> s.approved++
                    }
                    s.sold += soldByDesign[id] ?: 0
                }
            }
        return m
    }

    // ---------- sales ----------
    private fun toSale(c: Cursor) = Sale(
        c.l("txn_id"), c.l("receipt_id"), c.l("listing_id"), c.l("design_id"), c.s("title"), c.d("amount"),
        c.d("fees"), c.d("cost"), c.s("currency"), c.l("ts"), c.s("pf_order"), c.s("country"),
    )

    /** @return true if it was new. */
    fun insertSale(s: Sale): Boolean {
        val cv = ContentValues().apply {
            put("txn_id", s.txnId); put("receipt_id", s.receiptId); put("listing_id", s.listingId); put("design_id", s.designId)
            put("title", s.title); put("amount", s.amount); put("fees", s.fees); put("cost", s.cost); put("currency", s.currency)
            put("ts", s.ts); put("pf_order", s.pfOrder); put("country", s.country)
        }
        return writableDatabase.insertWithOnConflict("sales", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun setSaleCost(txnId: Long, cost: Double, pfOrder: String) {
        val cv = ContentValues().apply { put("cost", cost); put("pf_order", pfOrder) }
        writableDatabase.update("sales", cv, "txn_id=?", arrayOf(txnId.toString()))
    }

    fun sales(): List<Sale> = query("SELECT * FROM sales ORDER BY ts DESC", map = ::toSale)

    // ---------- research ----------
    fun saveResearch(items: List<ResearchItem>) {
        val w = writableDatabase
        w.beginTransaction()
        try {
            w.delete("research", "ts < ?", arrayOf((System.currentTimeMillis() - 30L * 86_400_000).toString()))
            for (r in items) {
                val cv = ContentValues().apply {
                    put("ts", r.ts); put("source", r.source); put("query", r.query); put("title", r.title); put("price", r.price); put("favs", r.favs)
                }
                w.insert("research", null, cv)
            }
            w.setTransactionSuccessful()
        } finally { w.endTransaction() }
    }

    fun latestResearch(): List<ResearchItem> = query(
        "SELECT * FROM research WHERE ts = (SELECT MAX(ts) FROM research) ORDER BY favs DESC",
    ) { ResearchItem(it.l("ts"), it.s("source"), it.s("query"), it.s("title"), it.d("price"), it.l("favs").toInt()) }

    // ---------- suggestions ----------
    fun suggestions(): List<Suggestion> =
        query("SELECT * FROM suggestions ORDER BY id DESC") { Suggestion(it.l("id"), it.l("ts"), it.s("text"), it.l("active") == 1L) }

    fun addSuggestion(text: String) {
        val cv = ContentValues().apply { put("ts", System.currentTimeMillis()); put("text", text); put("active", 1) }
        writableDatabase.insert("suggestions", null, cv)
    }

    fun setSuggestionActive(id: Long, active: Boolean) {
        val cv = ContentValues().apply { put("active", if (active) 1 else 0) }
        writableDatabase.update("suggestions", cv, "id=?", arrayOf(id.toString()))
    }

    fun deleteSuggestion(id: Long) { writableDatabase.delete("suggestions", "id=?", arrayOf(id.toString())) }

    // ---------- sources ----------
    fun isUsed(title: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM used_sources WHERE title=?", arrayOf(title)).use { it.moveToFirst() }

    fun markUsed(title: String) {
        val cv = ContentValues().apply { put("title", title) }
        writableDatabase.insertWithOnConflict("used_sources", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    // ---------- log ----------
    fun log(msg: String) {
        val cv = ContentValues().apply { put("ts", System.currentTimeMillis()); put("msg", msg.take(500)) }
        val w = writableDatabase
        w.insert("log", null, cv)
        w.execSQL("DELETE FROM log WHERE id <= (SELECT MAX(id) FROM log) - 300")
    }

    fun logs(limit: Int = 80): List<LogLine> =
        query("SELECT ts, msg FROM log ORDER BY id DESC LIMIT $limit") { LogLine(it.l("ts"), it.s("msg")) }
}
