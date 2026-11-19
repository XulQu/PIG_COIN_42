package com.bootybay.hallowmiss.net

import android.net.Uri
import android.util.Base64
import com.bootybay.hallowmiss.data.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/** Etsy Open API v3: OAuth (PKCE), listing creation, receipts, and market search. */
class Etsy(private val p: Prefs) {
    companion object {
        const val REDIRECT = "bootybay://etsy-callback"
        private const val BASE = "https://openapi.etsy.com/v3/application"
        private const val TOKEN = "https://api.etsy.com/v3/public/oauth/token"
        private const val SCOPES = "listings_w listings_r transactions_r shops_r"
    }

    val hasKey: Boolean get() = p.etsyKey.isNotBlank()
    val connected: Boolean get() = p.etsyRefresh.isNotBlank() && p.etsyShopId != 0L

    private val apiKey: String get() = if (p.etsySecret.isNotBlank()) "${p.etsyKey}:${p.etsySecret}" else p.etsyKey

    private fun h(auth: Boolean): Map<String, String> {
        val m = mutableMapOf("x-api-key" to apiKey)
        if (auth) m["Authorization"] = "Bearer ${token()}"
        return m
    }

    private fun token(): String {
        if (System.currentTimeMillis() > p.etsyExpires - 60_000) refresh()
        return p.etsyAccess
    }

    private fun saveToken(j: JSONObject) {
        p.etsyAccess = j.getString("access_token")
        p.etsyRefresh = j.optString("refresh_token", p.etsyRefresh)
        p.etsyExpires = System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000
    }

    private fun refresh() {
        if (p.etsyRefresh.isBlank()) throw Exception("Etsy not connected")
        saveToken(Http.sendForm(TOKEN, Http.form(
            "grant_type" to "refresh_token", "client_id" to p.etsyKey, "refresh_token" to p.etsyRefresh,
        ), mapOf("x-api-key" to apiKey)))
    }

    // ---------- OAuth ----------
    private fun random(n: Int): String {
        val b = ByteArray(n); SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun authUrl(): String {
        val verifier = random(48)
        val state = random(12)
        p.etsyVerifier = verifier
        p.etsyState = state
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        return "https://www.etsy.com/oauth/connect?response_type=code&redirect_uri=${Http.enc(REDIRECT)}" +
            "&scope=${Http.enc(SCOPES)}&client_id=${Http.enc(p.etsyKey)}&state=${Http.enc(state)}" +
            "&code_challenge=$challenge&code_challenge_method=S256"
    }

    /** Accepts either the full redirect URL or just the code. */
    fun finishAuth(codeOrUrl: String) {
        val code = if (codeOrUrl.contains("code=")) Uri.parse(codeOrUrl.trim()).getQueryParameter("code") ?: "" else codeOrUrl.trim()
        if (code.isBlank()) throw Exception("No code found")
        saveToken(Http.sendForm(TOKEN, Http.form(
            "grant_type" to "authorization_code", "client_id" to p.etsyKey, "redirect_uri" to REDIRECT,
            "code" to code, "code_verifier" to p.etsyVerifier,
        ), mapOf("x-api-key" to apiKey)))
        val me = get("/users/me")
        p.etsyShopId = me.optLong("shop_id")
        if (p.etsyShopId == 0L) throw Exception("Connected, but this Etsy account has no shop yet")
    }

    // ---------- plumbing ----------
    fun get(path: String, auth: Boolean = true): JSONObject = Http.getJson(BASE + path, h(auth))

    private fun send(path: String, method: String, vararg pairs: Pair<String, String?>): JSONObject =
        Http.sendForm(BASE + path, Http.form(*pairs), h(true), method)

    fun me(): JSONObject = get("/users/me")

    // ---------- market research ----------
    fun searchActive(keywords: String, limit: Int = 100): JSONArray =
        get("/listings/active?keywords=${Http.enc(keywords)}&limit=$limit&sort_on=score", auth = false)
            .optJSONArray("results") ?: JSONArray()

    // ---------- listing ----------
    private fun findNode(arr: JSONArray?, name: String): Long {
        if (arr == null) return 0
        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i) ?: continue
            if (n.optString("name").equals(name, true)) return n.optLong("id")
            val c = findNode(n.optJSONArray("children"), name)
            if (c != 0L) return c
        }
        return 0
    }

    /** Looks up (once) the taxonomy id for Mugs, a shipping profile and a processing profile. */
    private fun ensureSetup() {
        val shop = p.etsyShopId
        if (p.etsyTaxonomyId == 0L) {
            p.etsyTaxonomyId = findNode(get("/seller-taxonomy/nodes", auth = false).optJSONArray("results"), "Mugs")
            if (p.etsyTaxonomyId == 0L) throw Exception("Couldn't find Etsy 'Mugs' category")
        }
        if (p.etsyShippingProfileId == 0L) {
            val r = get("/shops/$shop/shipping-profiles").optJSONArray("results")
            p.etsyShippingProfileId = r?.optJSONObject(0)?.optLong("shipping_profile_id") ?: 0L
            if (p.etsyShippingProfileId == 0L) throw Exception("Create a shipping profile in Etsy (Printful has a template) then retry")
        }
        if (p.etsyReadinessId == 0L) {
            p.etsyReadinessId = try {
                val r = get("/shops/$shop/readiness-state-definitions").optJSONArray("results")
                r?.optJSONObject(0)?.optLong("readiness_state_id")?.takeIf { it != 0L }
                    ?: send("/shops/$shop/readiness-state-definitions", "POST",
                        "readiness_state" to "made_to_order", "min_processing_time" to "2",
                        "max_processing_time" to "5", "processing_time_unit" to "days",
                    ).optLong("readiness_state_id")
            } catch (e: Exception) { -1L } // older shops: processing lives on the shipping profile
        }
    }

    private fun productionPartners(): String? = try {
        val r = get("/shops/${p.etsyShopId}/production-partners").optJSONArray("results")
        if (r == null || r.length() == 0) null
        else (0 until r.length()).joinToString(",") { r.getJSONObject(it).optLong("production_partner_id").toString() }
    } catch (e: Exception) { null }

    fun createDraft(title: String, description: String, tags: List<String>, price: String): Long {
        ensureSetup()
        val res = send("/shops/${p.etsyShopId}/listings", "POST",
            "quantity" to "999",
            "title" to title,
            "description" to description,
            "price" to price,
            "who_made" to "i_did",
            "when_made" to "made_to_order",
            "is_supply" to "false",
            "type" to "physical",
            "taxonomy_id" to p.etsyTaxonomyId.toString(),
            "shipping_profile_id" to p.etsyShippingProfileId.toString(),
            "readiness_state_id" to p.etsyReadinessId.takeIf { it > 0 }?.toString(),
            "production_partner_ids" to productionPartners(),
            "materials" to "Ceramic",
            "tags" to tags.joinToString(","),
        )
        return res.getLong("listing_id")
    }

    fun uploadImage(listingId: Long, data: ByteArray, filename: String, rank: Int) {
        val mp = Http.Multipart().file("image", filename, "image/jpeg", data).field("rank", rank.toString())
        val body = mp.build()
        Http.json(Http.request("$BASE/shops/${p.etsyShopId}/listings/$listingId/images", "POST", h(true), body, mp.contentType, 90_000))
    }

    fun publish(listingId: Long) {
        send("/shops/${p.etsyShopId}/listings/$listingId", "PATCH", "state" to "active")
    }

    // ---------- sales ----------
    fun receipts(minCreatedSec: Long): JSONArray =
        get("/shops/${p.etsyShopId}/receipts?min_created=$minCreatedSec&was_paid=true&limit=100")
            .optJSONArray("results") ?: JSONArray()
}

fun money(o: JSONObject?): Double {
    if (o == null) return 0.0
    val div = o.optDouble("divisor", 100.0).takeIf { it > 0 } ?: 100.0
    return o.optDouble("amount", 0.0) / div
}
