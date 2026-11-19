package com.bootybay.hallowmiss.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Bumped whenever background work changes something, so the dashboard re-reads. */
object Events {
    val tick = MutableStateFlow(0)
    fun bump() { tick.value = tick.value + 1 }
}

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("bootybay", Context.MODE_PRIVATE)

    private inner class Str(val key: String, val def: String = "") : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String = sp.getString(key, def) ?: def
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) { sp.edit().putString(key, value).apply() }
    }
    private inner class Bool(val key: String, val def: Boolean) : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = sp.getBoolean(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { sp.edit().putBoolean(key, value).apply() }
    }
    private inner class Lng(val key: String, val def: Long = 0L) : ReadWriteProperty<Any?, Long> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Long = sp.getLong(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) { sp.edit().putLong(key, value).apply() }
    }
    private inner class Num(val key: String, val def: Int) : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = sp.getInt(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { sp.edit().putInt(key, value).apply() }
    }

    // --- Printful ---
    var printfulToken by Str("pf_token")
    var printfulStoreId by Str("pf_store")
    var printfulProductId by Str("pf_product", "19")      // White Glossy Mug
    var printfulVariantId by Str("pf_variant", "1320")    // 11oz
    var retailPrice by Str("price", "17.99")
    var autoConfirmOrders by Bool("pf_autoconfirm", false)

    // --- Etsy ---
    var etsyKey by Str("etsy_key")
    var etsySecret by Str("etsy_secret")
    var etsyAccess by Str("etsy_access")
    var etsyRefresh by Str("etsy_refresh")
    var etsyExpires by Lng("etsy_expires")
    var etsyShopId by Lng("etsy_shop")
    var etsyVerifier by Str("etsy_verifier")
    var etsyState by Str("etsy_state")
    var etsyTaxonomyId by Lng("etsy_taxonomy")
    var etsyShippingProfileId by Lng("etsy_shipping")
    var etsyReadinessId by Lng("etsy_readiness")
    var etsyPublish by Bool("etsy_publish", true)

    // --- Other services ---
    var imgbbKey by Str("imgbb_key")
    var ebayClientId by Str("ebay_id")
    var ebayClientSecret by Str("ebay_secret")

    // --- Automation ---
    var automate by Bool("automate", false)
    var batchSize by Num("batch_size", 10)
    var batchHours by Num("batch_hours", 24)
    var chargingOnly by Bool("charging_only", false)
    var pendingCap by Num("pending_cap", 30)

    // --- Runtime state ---
    var lastHeartbeat by Lng("hb_at")
    var lastHeartbeatOk by Bool("hb_ok", false)
    var lastNasaTime by Str("hb_nasa")
    var nasaDriftMs by Lng("hb_drift")
    var lastBatch by Lng("batch_at")
    var lastResearch by Lng("research_at")
    var lastSalesCheck by Lng("sales_at")   // epoch seconds
    var working by Str("working")
    var workingSince by Lng("working_since")

    // Research output (JSON)
    var trendsJson by Str("trends", "{}")      // motif -> share of top listings
    var termsJson by Str("terms", "[]")        // [[term,count],...]
    var pricesJson by Str("prices", "{}")      // source -> median price

    fun trends(): Map<String, Double> = try {
        val j = JSONObject(trendsJson); j.keys().asSequence().associateWith { j.optDouble(it, 0.0) }
    } catch (e: Exception) { emptyMap() }

    fun markWorking(text: String) {
        working = text
        workingSince = if (text.isEmpty()) 0L else System.currentTimeMillis()
        Events.bump()
    }

    /** Clears a "working" flag left behind if a worker was killed mid-run. */
    fun workingNow(): String {
        val w = working
        if (w.isNotEmpty() && System.currentTimeMillis() - workingSince > 45 * 60_000L) { working = ""; return "" }
        return w
    }
}
