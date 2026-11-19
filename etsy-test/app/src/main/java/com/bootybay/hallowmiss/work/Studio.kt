package com.bootybay.hallowmiss.work

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.bootybay.hallowmiss.data.Db
import com.bootybay.hallowmiss.data.Design
import com.bootybay.hallowmiss.data.Prefs
import com.bootybay.hallowmiss.data.Sale
import com.bootybay.hallowmiss.data.Status
import com.bootybay.hallowmiss.design.Etcher
import com.bootybay.hallowmiss.design.Ideas
import com.bootybay.hallowmiss.design.Motif
import com.bootybay.hallowmiss.design.MugComposer
import com.bootybay.hallowmiss.design.Tracer
import com.bootybay.hallowmiss.net.Commons
import com.bootybay.hallowmiss.net.Etsy
import com.bootybay.hallowmiss.net.Http
import com.bootybay.hallowmiss.net.ImageHost
import com.bootybay.hallowmiss.net.Printful
import com.bootybay.hallowmiss.net.money
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.random.Random

/** Makes one design: pick an idea → fetch two public-domain etchings → etch → vectorise → compose → save. */
class Studio(private val ctx: Context, private val prefs: Prefs, private val db: Db) {
    private val cache = HashMap<String, List<Commons.Img>>()
    private val rnd = Random(System.nanoTime())

    fun makeOne(): Long? {
        val idea = Ideas.next(Ideas.weights(db, prefs), rnd, db.recentCombos(), Ideas.customPhrases(db))
        val left = motif(Ideas.HALLOWEEN.getValue(idea.halloween)) ?: run { db.log("No usable etching for '${idea.halloween}'"); return null }
        val right = motif(Ideas.CHRISTMAS.getValue(idea.xmas)) ?: run { db.log("No usable etching for '${idea.xmas}'"); return null }

        val out = MugComposer.compose(MugComposer.Spec(idea.phrase, left, right, idea.palette, rnd.nextLong()))
        val dir = File(ctx.filesDir, "designs").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val png = File(dir, "$stamp.png")
        val thumb = File(dir, "${stamp}_t.jpg")
        val svg = File(dir, "$stamp.svgz")
        FileOutputStream(png).use { out.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val t = onWhite(out.bitmap, 810, 315)
        FileOutputStream(thumb).use { t.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        t.recycle()
        out.bitmap.recycle()
        GZIPOutputStream(FileOutputStream(svg)).use { it.write(out.svg.toByteArray()) }

        val credits = listOf(left.source, right.source).joinToString("\n") {
            "${it.title.removePrefix("File:")} · ${it.license}${if (it.artist.isNotBlank()) " · ${it.artist}" else ""} · ${it.pageUrl}"
        }
        return db.insertDesign(ContentValues().apply {
            put("created", stamp); put("phrase", idea.phrase); put("title", Ideas.title(idea))
            put("tags", Ideas.tags(idea, prefs).joinToString(",")); put("motif_h", idea.halloween); put("motif_x", idea.xmas)
            put("palette", idea.palette.name); put("credits", credits); put("png", png.path); put("thumb", thumb.path)
            put("svg", svg.path); put("status", Status.PENDING)
        })
    }

    private fun motif(queries: List<String>): Motif? {
        for (allowReuse in listOf(false, true)) {
            for (q in queries.shuffled(rnd)) {
                val list = cache.getOrPut(q) {
                    try { Commons.search(q) } catch (e: Exception) { db.log("Commons search '$q' failed: ${e.message}"); emptyList() }
                }
                val candidates = list.filter { allowReuse || !db.isUsed(it.title) }.shuffled(rnd).take(4)
                for (img in candidates) {
                    db.markUsed(img.title)
                    val bytes = try { Http.bytes(img.thumbUrl) } catch (e: Exception) { continue }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                    val etched = Etcher.etch(bmp)
                    bmp.recycle()
                    if (etched == null) continue
                    val loops = Tracer.trace(etched.mask, etched.w, etched.h)
                    if (loops.size < 15 || loops.size > 30_000) continue
                    return Motif(loops, etched.w, etched.h, img)
                }
            }
        }
        return null
    }

    companion object {
        fun onWhite(src: Bitmap, w: Int, h: Int): Bitmap {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(b).apply {
                drawColor(Color.WHITE)
                drawBitmap(src, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            }
            return b
        }

        fun deleteFiles(d: Design) { listOf(d.png, d.svg).forEach { if (it.isNotBlank()) File(it).delete() } }
    }
}

/** Approved design → hosted print file → Printful product + mockup → Etsy listing (+ publish). */
class Poster(private val prefs: Prefs, private val db: Db) {

    fun post(d0: Design) {
        var d = d0
        val pf = Printful(prefs)
        val etsy = Etsy(prefs)

        if (d.fileUrl.isBlank()) {
            if (prefs.imgbbKey.isBlank()) throw Exception("Add an imgbb API key in Settings (Printful needs a public file URL)")
            val url = ImageHost.upload(prefs.imgbbKey, File(d.png).readBytes(), "hallowmiss-${d.id}")
            db.update(d.id) { put("file_url", url) }
            d = d.copy(fileUrl = url)
        }
        if (pf.configured) {
            if (d.pfProduct == 0L) {
                val id = pf.createProduct("Hallowmiss – ${d.phrase}", d.fileUrl)
                db.update(d.id) { put("pf_product", id) }
                d = d.copy(pfProduct = id)
            }
            if (d.mockup.isBlank()) {
                val m = try { pf.mockup(d.fileUrl) } catch (e: Exception) { db.log("Mockup: ${e.message}"); null }
                if (m != null) { db.update(d.id) { put("mockup", m) }; d = d.copy(mockup = m) }
            }
        } else throw Exception("Add your Printful API token in Settings")

        if (!etsy.connected) throw Exception("Printful done. Connect Etsy in Settings to list it")
        if (d.listingId == 0L) {
            val lid = etsy.createDraft(d.title, descriptionFor(d), d.tags.split(",").filter { it.isNotBlank() }, prefs.retailPrice)
            db.update(d.id) { put("listing_id", lid) }
            d = d.copy(listingId = lid)
        }
        if (!d.imagesDone) {
            var rank = 1
            if (d.mockup.isNotBlank()) {
                try { etsy.uploadImage(d.listingId, Http.bytes(d.mockup), "hallowmiss-${d.id}-mockup.jpg", rank++) }
                catch (e: Exception) { db.log("Mockup upload: ${e.message}") }
            }
            etsy.uploadImage(d.listingId, flatJpeg(d), "hallowmiss-${d.id}-wrap.jpg", rank)
            db.update(d.id) { put("images_done", 1) }
        }
        if (prefs.etsyPublish) etsy.publish(d.listingId)
        db.update(d.id) { put("status", Status.POSTED); put("error", ""); put("posted_at", System.currentTimeMillis()) }
        db.log("Posted #${d.id} '${d.phrase}' → Etsy listing ${d.listingId}")
    }

    private fun descriptionFor(d: Design): String {
        val idea = Ideas.Idea(d.phrase, d.motifH, d.motifX, Ideas.PALETTES.first())
        return Ideas.description(idea)
    }

    private fun flatJpeg(d: Design): ByteArray {
        val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
        val src = BitmapFactory.decodeFile(d.png, opts) ?: throw Exception("Design file missing")
        val flat = Studio.onWhite(src, 2000, 778)
        src.recycle()
        val bos = ByteArrayOutputStream()
        flat.compress(Bitmap.CompressFormat.JPEG, 88, bos)
        flat.recycle()
        return bos.toByteArray()
    }
}

/** Pulls new paid Etsy receipts, records them, notifies, and forwards BootyBay mugs to Printful. */
object SalesSync {
    fun run(ctx: Context, prefs: Prefs, db: Db) {
        val etsy = Etsy(prefs)
        if (!etsy.connected) return
        val nowSec = System.currentTimeMillis() / 1000
        val since = if (prefs.lastSalesCheck == 0L) nowSec - 7 * 86_400 else prefs.lastSalesCheck - 3_600
        val receipts = etsy.receipts(since)
        val pf = Printful(prefs)

        for (i in 0 until receipts.length()) {
            val r = receipts.getJSONObject(i)
            val rid = r.optLong("receipt_id")
            val txns = r.optJSONArray("transactions") ?: JSONArray()
            val shipping = money(r.optJSONObject("total_shipping_cost"))
            val fresh = ArrayList<Pair<Sale, Design?>>()
            for (k in 0 until txns.length()) {
                val t = txns.getJSONObject(k)
                val qty = t.optInt("quantity", 1)
                val amount = money(t.optJSONObject("price")) * qty + if (k == 0) shipping else 0.0
                val design = db.designByListing(t.optLong("listing_id"))
                // Etsy: 6.5% transaction + ~3% + $0.25 payment processing + $0.20 auto-renew on sale.
                val fees = amount * 0.095 + 0.25 / txns.length() + 0.20 * qty
                val sale = Sale(
                    t.optLong("transaction_id"), rid, t.optLong("listing_id"), design?.id ?: 0L, t.optString("title"),
                    amount, fees, 0.0, t.optJSONObject("price")?.optString("currency_code") ?: "USD",
                    r.optLong("create_timestamp", nowSec) * 1000, "", r.optString("country_iso"),
                )
                if (db.insertSale(sale)) fresh.add(sale to design)
            }
            if (fresh.isEmpty()) continue

            val total = fresh.sumOf { it.first.amount }
            Notify.sale(ctx, "💀 Sale! ${"%.2f".format(total)} ${fresh.first().first.currency}", fresh.joinToString("\n") { it.first.title.take(60) })
            db.log("Sale: receipt $rid, ${fresh.size} item(s), ${"%.2f".format(total)}")

            val ours = fresh.filter { it.second?.fileUrl?.isNotBlank() == true }
            if (ours.isEmpty() || !pf.configured) continue
            try {
                val recipient = JSONObject()
                    .put("name", r.optString("name")).put("address1", r.optString("first_line"))
                    .put("address2", r.optString("second_line")).put("city", r.optString("city"))
                    .put("state_code", r.optString("state")).put("country_code", r.optString("country_iso"))
                    .put("zip", r.optString("zip"))
                val items = JSONArray()
                for ((sale, design) in ours) {
                    val qty = txns.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.optLong("transaction_id") == sale.txnId }?.optInt("quantity", 1) ?: 1 }
                    items.put(pf.orderItem(design!!.fileUrl, qty, design.phrase, prefs.retailPrice))
                }
                val order = pf.order("etsy-$rid", recipient, items, prefs.autoConfirmOrders)
                val cost = order.optJSONObject("costs")?.optString("total")?.toDoubleOrNull() ?: 0.0
                db.setSaleCost(ours.first().first.txnId, cost, order.optLong("id").toString())
                val what = if (prefs.autoConfirmOrders) "sent to production" else "created as a draft. Confirm it in Printful"
                Notify.sale(ctx, "Printful order $what", "Etsy receipt $rid · cost ${"%.2f".format(cost)}")
            } catch (e: Exception) {
                db.log("Printful order for receipt $rid failed: ${e.message}")
                Notify.sale(ctx, "⚠ Fulfil manually", "Couldn't create the Printful order for Etsy receipt $rid: ${e.message?.take(80)}")
            }
        }
        prefs.lastSalesCheck = nowSec
    }
}

/** Reads the top ~100 Christmas / Halloween mugs on Etsy (+ eBay if configured) and distils trends. */
object Research {
    private val ETSY_QUERIES = listOf("christmas mug", "halloween mug", "creepy christmas mug", "gothic christmas mug", "spooky christmas mug")
    private val EBAY_QUERIES = listOf("christmas mug", "halloween mug", "gothic christmas mug")
    private val STOP = setOf(
        "mug", "mugs", "cup", "cups", "coffee", "tea", "gift", "gifts", "for", "the", "and", "with", "oz", "11oz", "15oz",
        "a", "of", "to", "in", "her", "him", "his", "she", "he", "personalized", "custom", "ceramic", "new", "funny", "cute",
        "christmas", "halloween", "xmas", "holiday", "present", "women", "men", "mom", "dad", "friend", "on", "is", "it", "your",
    )

    fun run(prefs: Prefs, db: Db): String {
        val ts = System.currentTimeMillis()
        val items = ArrayList<com.bootybay.hallowmiss.data.ResearchItem>()
        val texts = ArrayList<String>()
        val prices = HashMap<String, MutableList<Double>>()
        val notes = ArrayList<String>()

        val etsy = Etsy(prefs)
        if (etsy.hasKey) {
            for (q in ETSY_QUERIES) {
                try {
                    val arr = etsy.searchActive(q, 100)
                    for (i in 0 until arr.length()) {
                        val l = arr.getJSONObject(i)
                        val title = l.optString("title")
                        val tagArr = l.optJSONArray("tags")
                        val tags = if (tagArr == null) "" else (0 until tagArr.length()).joinToString(" ") { tagArr.optString(it) }
                        val price = money(l.optJSONObject("price"))
                        items.add(com.bootybay.hallowmiss.data.ResearchItem(ts, "etsy", q, title.take(140), price, l.optInt("num_favorers")))
                        texts.add("$title $tags")
                        prices.getOrPut("etsy") { ArrayList() }.add(price)
                    }
                } catch (e: Exception) { notes.add("Etsy '$q': ${e.message?.take(80)}") }
            }
        } else notes.add("Etsy keystring missing: skipped Etsy")

        val ebay = com.bootybay.hallowmiss.net.Ebay(prefs)
        if (ebay.configured) {
            try {
                for ((q, arr) in ebay.search(EBAY_QUERIES)) {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val title = o.optString("title")
                        val price = o.optJSONObject("price")?.optString("value")?.toDoubleOrNull() ?: 0.0
                        items.add(com.bootybay.hallowmiss.data.ResearchItem(ts, "ebay", q, title.take(140), price, 0))
                        texts.add(title)
                        prices.getOrPut("ebay") { ArrayList() }.add(price)
                    }
                }
            } catch (e: Exception) { notes.add("eBay: ${e.message?.take(80)}") }
        }

        if (items.isEmpty()) return "No research data. " + notes.joinToString("; ")

        // Motif share across all listings.
        val trends = JSONObject()
        for (m in Ideas.ALL) {
            val hits = texts.count { m in Ideas.keywordsIn(it) }
            trends.put(m, hits.toDouble() / texts.size)
        }
        // Top words and two-word phrases, trademarks removed.
        val counts = HashMap<String, Int>()
        for (t in texts) {
            val words = t.lowercase().replace(Regex("[^a-z ]"), " ").split(" ").filter { it.length > 2 }
            val seen = HashSet<String>()
            for ((i, w) in words.withIndex()) {
                if (w !in STOP) seen.add(w)
                if (i + 1 < words.size && w !in STOP && words[i + 1] !in STOP) seen.add("$w ${words[i + 1]}")
            }
            seen.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        val terms = JSONArray()
        counts.entries.filter { !Ideas.isTrademarked(it.key) && it.value >= 3 }
            .sortedByDescending { it.value }.take(40)
            .forEach { terms.put(JSONArray().put(it.key).put(it.value)) }
        val med = JSONObject()
        prices.forEach { (src, list) -> val s = list.filter { it > 0 }.sorted(); if (s.isNotEmpty()) med.put(src, s[s.size / 2]) }

        prefs.trendsJson = trends.toString()
        prefs.termsJson = terms.toString()
        prefs.pricesJson = med.toString()
        db.saveResearch(items)
        return "Read ${items.size} listings." + if (notes.isNotEmpty()) " " + notes.joinToString("; ") else ""
    }
}
