package com.bootybay.hallowmiss.net

import com.bootybay.hallowmiss.data.Prefs
import org.json.JSONArray
import org.json.JSONObject

class Printful(private val p: Prefs) {
    private val base = "https://api.printful.com"
    val configured: Boolean get() = p.printfulToken.isNotBlank()

    private fun h(): Map<String, String> {
        val m = mutableMapOf("Authorization" to "Bearer ${p.printfulToken}")
        if (p.printfulStoreId.isNotBlank()) m["X-PF-Store-Id"] = p.printfulStoreId
        return m
    }

    fun stores(): JSONArray =
        Http.getJson("$base/stores", mapOf("Authorization" to "Bearer ${p.printfulToken}")).optJSONArray("result") ?: JSONArray()

    /** Creates a product in the Printful (Manual/API) store so the design lives in your Printful library. */
    fun createProduct(name: String, fileUrl: String): Long {
        val body = JSONObject()
            .put("sync_product", JSONObject().put("name", name.take(60)).put("thumbnail", fileUrl))
            .put("sync_variants", JSONArray().put(
                JSONObject()
                    .put("variant_id", p.printfulVariantId.toLong())
                    .put("retail_price", p.retailPrice)
                    .put("files", JSONArray().put(JSONObject().put("url", fileUrl)))
            ))
        return Http.sendJson("$base/store/products", body, h()).getJSONObject("result").optLong("id")
    }

    /** Generates a product mockup. Polls a few times; returns null if it isn't ready in ~90s. */
    fun mockup(fileUrl: String): String? {
        val body = JSONObject()
            .put("variant_ids", JSONArray().put(p.printfulVariantId.toLong()))
            .put("format", "jpg")
            .put("files", JSONArray().put(JSONObject().put("placement", "default").put("image_url", fileUrl)))
        val task = Http.sendJson("$base/mockup-generator/create-task/${p.printfulProductId}", body, h())
            .getJSONObject("result").getString("task_key")
        repeat(18) {
            Thread.sleep(5_000)
            val r = Http.getJson("$base/mockup-generator/task?task_key=${Http.enc(task)}", h()).getJSONObject("result")
            when (r.optString("status")) {
                "completed" -> return r.optJSONArray("mockups")?.optJSONObject(0)?.optString("mockup_url")
                "failed" -> throw Exception("Printful mockup failed: ${r.optString("error")}")
            }
        }
        return null
    }

    /** Creates a Printful order for an Etsy sale. Returns the "result" object. */
    fun order(externalId: String, recipient: JSONObject, items: JSONArray, confirm: Boolean): JSONObject {
        val body = JSONObject().put("external_id", externalId).put("recipient", recipient).put("items", items)
        return Http.sendJson("$base/orders?confirm=$confirm", body, h()).getJSONObject("result")
    }

    fun orderItem(fileUrl: String, quantity: Int, name: String, retail: String): JSONObject = JSONObject()
        .put("variant_id", p.printfulVariantId.toLong())
        .put("quantity", quantity)
        .put("name", name.take(80))
        .put("retail_price", retail)
        .put("files", JSONArray().put(JSONObject().put("url", fileUrl)))
}
