package com.bootybay.hallowmiss.net

import android.util.Base64
import com.bootybay.hallowmiss.data.Prefs
import org.json.JSONArray

/** imgbb: free public image hosting. Printful only accepts print files by URL, so we need one. */
object ImageHost {
    fun upload(key: String, png: ByteArray, name: String): String {
        val b64 = Base64.encodeToString(png, Base64.NO_WRAP)
        val j = Http.sendForm("https://api.imgbb.com/1/upload?key=${Http.enc(key)}", Http.form("image" to b64, "name" to name))
        return j.getJSONObject("data").getString("url")
    }
}

/** eBay Browse API (application token) for market research. */
class Ebay(private val p: Prefs) {
    val configured: Boolean get() = p.ebayClientId.isNotBlank() && p.ebayClientSecret.isNotBlank()

    private fun token(): String {
        val basic = Base64.encodeToString("${p.ebayClientId}:${p.ebayClientSecret}".toByteArray(), Base64.NO_WRAP)
        val j = Http.sendForm(
            "https://api.ebay.com/identity/v1/oauth2/token",
            Http.form("grant_type" to "client_credentials", "scope" to "https://api.ebay.com/oauth/api_scope"),
            mapOf("Authorization" to "Basic $basic"),
        )
        return j.getString("access_token")
    }

    fun search(queries: List<String>): Map<String, JSONArray> {
        val t = token()
        return queries.associateWith { q ->
            Http.getJson(
                "https://api.ebay.com/buy/browse/v1/item_summary/search?q=${Http.enc(q)}&limit=100",
                mapOf("Authorization" to "Bearer $t", "X-EBAY-C-MARKETPLACE-ID" to "EBAY_US"),
            ).optJSONArray("itemSummaries") ?: JSONArray()
        }
    }
}
