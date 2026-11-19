package com.bootybay.hallowmiss.design

import com.bootybay.hallowmiss.data.Db
import com.bootybay.hallowmiss.data.Prefs
import com.bootybay.hallowmiss.data.Status
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.random.Random

/** What to make: motifs, phrases, palettes, weighting, listing copy and the trademark guard. */
object Ideas {

    /** Halloween-side motifs → Wikimedia Commons searches (public-domain etchings / woodcuts). */
    val HALLOWEEN: Map<String, List<String>> = linkedMapOf(
        "witch" to listOf("witch engraving", "witches sabbath woodcut", "witch broomstick engraving", "witches illustration 17th century"),
        "skull" to listOf("skull engraving", "memento mori engraving", "skull woodcut", "vanitas engraving skull"),
        "skeleton" to listOf("danse macabre woodcut", "skeleton engraving", "dance of death holbein", "totentanz"),
        "bat" to listOf("bat engraving", "bats illustration 19th century", "chiroptera engraving"),
        "raven" to listOf("raven engraving", "crow engraving", "raven gustave dore"),
        "devil" to listOf("devil woodcut", "demon engraving", "devil engraving medieval"),
        "krampus" to listOf("krampus postcard", "krampus illustration", "perchten"),
        "black cat" to listOf("black cat illustration", "cat engraving 19th century", "witch cat woodcut"),
        "owl" to listOf("owl engraving", "owl woodcut"),
        "ghost" to listOf("ghost engraving", "spectre illustration 19th century", "phantom engraving"),
        "moth" to listOf("death's-head hawkmoth illustration", "moth engraving"),
    )

    /** Christmas-side motifs. */
    val CHRISTMAS: Map<String, List<String>> = linkedMapOf(
        "santa" to listOf("santa claus thomas nast", "father christmas engraving", "saint nicholas engraving"),
        "tree" to listOf("christmas tree engraving", "fir tree engraving", "christmas tree woodcut"),
        "holly" to listOf("holly botanical engraving", "ilex aquifolium illustration"),
        "mistletoe" to listOf("mistletoe botanical illustration", "viscum album engraving"),
        "reindeer" to listOf("reindeer engraving", "reindeer woodcut"),
        "bells" to listOf("bells engraving", "church bells woodcut"),
        "wreath" to listOf("wreath engraving", "laurel wreath engraving"),
        "candles" to listOf("candle engraving", "candlestick engraving"),
        "winter" to listOf("winter landscape engraving", "snow scene woodcut"),
    )

    val ALL: List<String> get() = HALLOWEEN.keys.toList() + CHRISTMAS.keys

    /** Words that count as a mention of each motif (research titles, suggestions, feedback). */
    private val SYNONYMS: Map<String, List<String>> = mapOf(
        "witch" to listOf("witch", "witchy", "coven", "broom", "spell", "witches"),
        "skull" to listOf("skull", "skulls", "memento mori", "vanitas"),
        "skeleton" to listOf("skeleton", "skeletons", "bones", "danse macabre", "macabre"),
        "bat" to listOf("bat", "bats"),
        "raven" to listOf("raven", "crow", "ravens", "crows"),
        "devil" to listOf("devil", "demon", "satan", "baphomet", "hell"),
        "krampus" to listOf("krampus"),
        "black cat" to listOf("black cat", "cat", "cats"),
        "owl" to listOf("owl", "owls"),
        "ghost" to listOf("ghost", "ghosts", "ghoul", "haunted", "spooky", "boo"),
        "moth" to listOf("moth", "moths"),
        "santa" to listOf("santa", "claus", "father christmas", "st nick"),
        "tree" to listOf("christmas tree", "tree", "pine", "fir"),
        "holly" to listOf("holly"),
        "mistletoe" to listOf("mistletoe"),
        "reindeer" to listOf("reindeer", "deer", "sleigh"),
        "bells" to listOf("bell", "bells", "jingle"),
        "wreath" to listOf("wreath"),
        "candles" to listOf("candle", "candles"),
        "winter" to listOf("snow", "winter", "snowy"),
    )

    /** Phrase, optional motif it suits. */
    private val PHRASES: List<Pair<String, String?>> = listOf(
        "Merry Krampus" to "krampus",
        "Krampus Is Coming" to "krampus",
        "Naughty & Damned" to "krampus",
        "Creepy Christmas" to null,
        "Season's Screamings" to null,
        "Jingle Hells" to "devil",
        "Fa La La La Boo" to "ghost",
        "Ho Ho Horror" to null,
        "Merry & Macabre" to null,
        "Memento Mori, Merry Christmas" to "skull",
        "Deck the Halls with Skulls" to "skull",
        "Dead Inside but Festive" to "skeleton",
        "Silent Night, Spooky Night" to null,
        "Yule Ghoul" to "ghost",
        "Witchmas" to "witch",
        "Have a Witchy Christmas" to "witch",
        "Coven Christmas Club" to "witch",
        "Tis the Season to Be Spooky" to null,
        "Goth Ho Ho" to null,
        "Hallowmiss" to null,
        "Nevermore Christmas" to "raven",
        "Bat Tidings of Great Joy" to "bat",
        "Feliz Navi-Dead" to "skeleton",
        "Oh Deadly Night" to null,
        "Dashing Through the Bones" to "skeleton",
        "Have a Haunted Holiday" to "ghost",
        "Sleigh the Darkness" to null,
        "Hex the Halls" to "witch",
        "Moth to the Christmas Flame" to "moth",
        "Ghoul Tidings" to "ghost",
    )

    /** Brands/IP that get Etsy listings taken down. Filtered from research terms, suggestions and phrases. */
    val TRADEMARKS = listOf(
        "disney", "nightmare before", "jack skellington", "grinch", "hocus pocus", "sanderson", "harry potter",
        "stranger things", "beetlejuice", "addams", "wednesday addams", "coraline", "tim burton", "marvel",
        "star wars", "pokemon", "hello kitty", "sanrio", "kuromi", "peanuts", "snoopy", "home alone",
        "taylor swift", "swiftie", "starbucks", "michael myers", "freddy krueger", "jason voorhees", "chucky",
        "pennywise", "ghostface", "halloweentown", "labubu", "practical magic", "elf on the shelf", "barbie",
    )

    fun isTrademarked(text: String): Boolean { val t = text.lowercase(); return TRADEMARKS.any { t.contains(it) } }

    data class Palette(val name: String, val inkA: Int, val inkB: Int, val accent: Int)

    val PALETTES = listOf(
        Palette("Ink & Blood", 0xFF111111.toInt(), 0xFF6B0F1A.toInt(), 0xFFB3121F.toInt()),
        Palette("Pine & Bone", 0xFF111111.toInt(), 0xFF0F3D2E.toInt(), 0xFF1E6B3A.toInt()),
        Palette("Midnight Holly", 0xFF1B1F3B.toInt(), 0xFF0F3D2E.toInt(), 0xFFB3121F.toInt()),
        Palette("Oxblood", 0xFF2A0A0F.toInt(), 0xFF6B0F1A.toInt(), 0xFF111111.toInt()),
        Palette("Gilded Grave", 0xFF111111.toInt(), 0xFF111111.toInt(), 0xFFA8781C.toInt()),
    )

    data class Idea(val phrase: String, val halloween: String, val xmas: String, val palette: Palette)

    fun keywordsIn(text: String): Set<String> {
        val t = " " + text.lowercase().replace(Regex("[^a-z' ]"), " ") + " "
        return SYNONYMS.filter { (_, words) -> words.any { t.contains(" $it ") } }.keys
    }

    private fun activeSuggestions(db: Db) = db.suggestions().filter { it.active && !isTrademarked(it.text) }

    private val NEGATIVE = Regex("\\b(less|fewer|no|stop|avoid|not|never|hate|drop|ditch)\\b", RegexOption.IGNORE_CASE)

    fun isNegative(text: String) = NEGATIVE.containsMatchIn(text)

    /** Motifs to boost (and to suppress, for suggestions like "fewer owls"). */
    fun focusMotifs(db: Db): Pair<Set<String>, Set<String>> {
        val s = activeSuggestions(db)
        val neg = s.filter { isNegative(it.text) }.flatMap { keywordsIn(it.text) }.toSet()
        val pos = s.filter { !isNegative(it.text) }.flatMap { keywordsIn(it.text) }.toSet()
        return pos to neg
    }

    /** Text in quotes inside a suggestion becomes a custom mug phrase. */
    fun customPhrases(db: Db): List<String> = activeSuggestions(db).flatMap { s ->
        Regex("[\"“”]([^\"“”]{3,40})[\"“”]").findAll(s.text).map { it.groupValues[1].trim() }.toList()
    }

    /** Motif weights from: market trends, your approvals/rejections, sales, and the suggestion box. */
    fun weights(db: Db, prefs: Prefs): Map<String, Double> {
        val stats = db.motifStats()
        val trends = prefs.trends()
        val (focus, avoid) = focusMotifs(db)
        return ALL.associateWith { m ->
            var w = 1.0
            trends[m]?.let { w *= 1.0 + 4.0 * it }
            stats[m]?.let { s ->
                w *= 2.0 * (s.approved + 1.0) / (s.approved + s.rejected + 2.0)
                w *= 1.0 + 0.5 * s.sold
            }
            if (m in focus) w *= 3.0
            if (m in avoid) w *= 0.15
            w
        }
    }

    private fun pick(keys: Collection<String>, weights: Map<String, Double>, rnd: Random): String {
        if (rnd.nextDouble() < 0.15) return keys.random(rnd) // keep exploring
        val total = keys.sumOf { weights[it] ?: 1.0 }
        var r = rnd.nextDouble() * total
        for (k in keys) { r -= weights[k] ?: 1.0; if (r <= 0) return k }
        return keys.last()
    }

    fun next(weights: Map<String, Double>, rnd: Random, recent: Set<String>, custom: List<String>): Idea {
        repeat(12) {
            val h = pick(HALLOWEEN.keys, weights, rnd)
            val x = pick(CHRISTMAS.keys, weights, rnd)
            val phrase = if (custom.isNotEmpty() && rnd.nextDouble() < 0.35) custom.random(rnd) else {
                val suited = PHRASES.filter { it.second == h || it.second == x }
                if (suited.isNotEmpty() && rnd.nextBoolean()) suited.random(rnd).first
                else PHRASES.filter { it.second == null || it.second == h }.random(rnd).first
            }
            if ("$phrase|$h|$x" !in recent && !isTrademarked(phrase)) return Idea(phrase, h, x, PALETTES.random(rnd))
        }
        return Idea(PHRASES.random(rnd).first, HALLOWEEN.keys.random(rnd), CHRISTMAS.keys.random(rnd), PALETTES.random(rnd))
    }

    // ---------- listing copy ----------
    private fun cap(s: String) = s.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    fun title(i: Idea): String {
        val t = "${i.phrase} Mug, Creepy Christmas ${cap(i.halloween)} Coffee Mug, Gothic ${cap(i.xmas)} Holiday Gift, " +
            "Spooky Xmas Cup, Witchy Goth Christmas Present"
        if (t.length <= 140) return t
        val cut = t.take(140)
        return cut.substring(0, cut.lastIndexOf(',').takeIf { it > 40 } ?: cut.length).trim()
    }

    fun tags(i: Idea, prefs: Prefs): List<String> {
        val trending = try {
            val arr = JSONArray(prefs.termsJson)
            (0 until minOf(arr.length(), 30)).map { arr.getJSONArray(it).getString(0) }
        } catch (e: Exception) { emptyList() }
        val base = listOf(
            "creepy christmas", "${i.halloween} mug", "${i.xmas} mug", "gothic christmas", "spooky christmas",
            "halloween christmas", "goth coffee mug", "witchy gift", "creepmas", "horror christmas",
            "memento mori", "dark academia mug", "krampus gift",
        )
        val all = (base.take(6) + trending.filter { it.contains(" ") }.take(3) + base.drop(6))
        return all.map { it.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim() }
            .filter { it.length in 3..20 && !isTrademarked(it) }
            .distinct().take(13)
    }

    fun description(i: Idea): String = """
        ${i.phrase} — a Hallowmiss creepy-Christmas mug for people who keep the skulls out all December.

        Artwork: antique ${i.halloween} and ${i.xmas} etchings, restored and vectorised by hand from public-domain originals, then dusted in snow and dark holiday trimmings. Printed on both sides.

        • 11 oz white glossy ceramic mug
        • Dishwasher and microwave safe
        • Printed to order and shipped by our print partner
        • Great gift for goths, witches, horror fans and anyone who thinks Christmas needs more skeletons

        Colors may vary slightly between screens. Made to order, so please check the preview before buying.
    """.trimIndent()

    // ---------- advisor ----------
    fun advice(db: Db, prefs: Prefs): List<String> {
        val out = ArrayList<String>()
        val counts = db.counts()
        val pending = counts[Status.PENDING] ?: 0
        val failed = counts[Status.FAILED] ?: 0
        val stats = db.motifStats()
        val trends = prefs.trends()
        val totalMade = stats.values.sumOf { it.made }.coerceAtLeast(1) / 2.0

        if (prefs.printfulToken.isBlank()) out.add("Setup: add your Printful API token in Settings so approved designs can go to print.")
        if (prefs.imgbbKey.isBlank()) out.add("Setup: add a free imgbb API key. Printful only accepts print files by URL.")
        if (prefs.etsyKey.isBlank()) out.add("Setup: add your Etsy API keystring. It also unlocks R&D on top-selling Etsy mugs.")
        else if (prefs.etsyRefresh.isBlank()) out.add("Setup: tap 'Connect Etsy' in Settings so approvals become listings and sales show up here.")

        if (pending >= prefs.pendingCap) out.add("$pending designs are waiting for review. Auto-generation is paused until you approve or reject some (every decision trains the generator).")
        else if (pending > 15) out.add("$pending designs are waiting. Reviewing them trains the generator toward what you like.")
        if (failed > 0) out.add("$failed posts failed. Open the Post tab to see why and retry.")

        trends.entries.sortedByDescending { it.value }.take(6).forEach { (m, share) ->
            val ours = (stats[m]?.made ?: 0) / totalMade
            if (share > 0.08 && ours < share / 2) out.add("Trending: '$m' is in ${(share * 100).toInt()}% of top-selling listings but only ${(ours * 100).toInt()}% of your designs. Boosting it.")
        }
        stats.filter { it.value.approved + it.value.rejected >= 5 }.forEach { (m, s) ->
            val rate = s.approved * 100 / (s.approved + s.rejected)
            if (rate < 30) out.add("You reject most '$m' designs ($rate% approved), so it's being deprioritised.")
            if (rate > 80) out.add("You approve $rate% of '$m' designs, so it's weighted up.")
        }
        stats.filter { it.value.sold > 0 }.maxByOrNull { it.value.sold }?.let { (m, s) ->
            out.add("Best seller motif: '$m' (${s.sold} sold). Making more of it.")
        }
        try {
            val prices = JSONObject(prefs.pricesJson)
            val etsy = prices.optDouble("etsy", 0.0)
            val mine = prefs.retailPrice.toDoubleOrNull() ?: 0.0
            if (etsy > 0 && mine > 0) {
                if (mine > etsy * 1.25) out.add("Your price ($${"%.2f".format(mine)}) is well above the Etsy median ($${"%.2f".format(etsy)}). Consider lowering it.")
                else if (mine < etsy * 0.8) out.add("Your price ($${"%.2f".format(mine)}) is below the Etsy median ($${"%.2f".format(etsy)}). There's room to raise it.")
            }
        } catch (_: Exception) {}

        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        when {
            month == Calendar.NOVEMBER || (month == Calendar.DECEMBER && day <= 12) ->
                out.add("Peak window: Christmas gifting. Printful's US standard shipping cutoffs land mid-December, so push volume now and approve fast.")
            month == Calendar.DECEMBER ->
                out.add("Past the shipping cutoffs. Pivot to 'creepy cute' Valentine's designs (skull hearts, anatomical hearts) and Friday-the-13th drops.")
            month in Calendar.JANUARY..Calendar.MARCH ->
                out.add("Off-season: build your back catalogue now so listings have reviews and history by autumn.")
            month in Calendar.JULY..Calendar.SEPTEMBER ->
                out.add("Halloween ramp-up: shoppers start searching in August. Get Halloween-leaning designs live early.")
        }
        if (out.isEmpty()) out.add("All good. Keep reviewing designs and the generator will keep learning.")
        return out
    }
}
