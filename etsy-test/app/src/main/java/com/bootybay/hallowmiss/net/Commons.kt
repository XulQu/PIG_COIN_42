package com.bootybay.hallowmiss.net

/** Wikimedia Commons search, restricted to public-domain / CC0 bitmaps so designs are safe to sell. */
object Commons {
    data class Img(val title: String, val thumbUrl: String, val pageUrl: String, val license: String, val artist: String)

    fun search(query: String, limit: Int = 30): List<Img> {
        val q = "$query filetype:bitmap -photograph -photo"
        val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2" +
            "&generator=search&gsrnamespace=6&gsrlimit=$limit&gsrsearch=${Http.enc(q)}" +
            "&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=1000" +
            "&iiextmetadatafilter=LicenseShortName%7CArtist"
        val j = Http.getJson(url)
        val pages = j.optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        val out = ArrayList<Img>()
        for (i in 0 until pages.length()) {
            val page = pages.optJSONObject(i) ?: continue
            val ii = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val thumb = ii.optString("thumburl").ifEmpty { ii.optString("url") }
            if (thumb.isEmpty()) continue
            val meta = ii.optJSONObject("extmetadata")
            val license = meta?.optJSONObject("LicenseShortName")?.optString("value").orEmpty()
            if (!isFree(license)) continue
            val artist = meta?.optJSONObject("Artist")?.optString("value").orEmpty()
                .replace(Regex("<[^>]+>"), "").trim().take(80)
            out.add(Img(page.optString("title"), thumb, ii.optString("descriptionurl"), license, artist))
        }
        return out
    }

    fun isFree(license: String): Boolean {
        val s = license.lowercase()
        return s.contains("public domain") || s.startsWith("pd") || s.contains("cc0") || s.contains("no restrictions")
    }
}
