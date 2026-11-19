package com.bootybay.hallowmiss.net

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL
import java.net.URLEncoder

class HttpException(val code: Int, body: String) : Exception("HTTP $code: ${body.take(400)}")

class Resp(val code: Int, val body: ByteArray, private val headers: Map<String?, List<String>>) {
    val text: String get() = String(body, Charsets.UTF_8)
    val ok: Boolean get() = code in 200..299
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key != null && it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
}

/** Tiny HTTP helper on top of HttpURLConnection: no extra libraries, nothing kept alive. */
object Http {
    const val UA = "BootyBay/1.0 (Android print-on-demand studio)"
    const val FORM = "application/x-www-form-urlencoded"
    const val JSON = "application/json"

    fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        contentType: String? = null,
        timeoutMs: Int = 30_000,
    ): Resp {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            setMethod(c, method)
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "*/*")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.doOutput = true
                if (contentType != null) c.setRequestProperty("Content-Type", contentType)
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val stream = if (code >= 400) c.errorStream else c.inputStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            return Resp(code, bytes, c.headerFields ?: emptyMap())
        } finally {
            c.disconnect()
        }
    }

    private fun setMethod(c: HttpURLConnection, m: String) {
        try {
            c.requestMethod = m
        } catch (e: ProtocolException) {
            // Some HttpURLConnection implementations refuse PATCH; set the field directly.
            val f = HttpURLConnection::class.java.getDeclaredField("method")
            f.isAccessible = true
            f.set(c, m)
        }
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun form(vararg pairs: Pair<String, String?>): ByteArray =
        pairs.filter { it.second != null }.joinToString("&") { enc(it.first) + "=" + enc(it.second!!) }.toByteArray()

    fun bytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray {
        val r = request(url, headers = headers, timeoutMs = 45_000)
        if (!r.ok) throw HttpException(r.code, r.text)
        return r.body
    }

    fun json(r: Resp): JSONObject {
        if (!r.ok) throw HttpException(r.code, r.text)
        return if (r.body.isEmpty()) JSONObject() else JSONObject(r.text)
    }

    fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject =
        json(request(url, headers = headers + ("Accept" to JSON)))

    fun sendJson(url: String, body: JSONObject, headers: Map<String, String> = emptyMap(), method: String = "POST"): JSONObject =
        json(request(url, method, headers + ("Accept" to JSON), body.toString().toByteArray(), JSON))

    fun sendForm(url: String, form: ByteArray, headers: Map<String, String> = emptyMap(), method: String = "POST"): JSONObject =
        json(request(url, method, headers + ("Accept" to JSON), form, FORM, 60_000))

    class Multipart {
        private val boundary = "----BootyBay" + System.nanoTime()
        private val out = ByteArrayOutputStream()
        val contentType: String get() = "multipart/form-data; boundary=$boundary"

        fun field(name: String, value: String) = apply {
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
        }

        fun file(name: String, filename: String, mime: String, data: ByteArray) = apply {
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\nContent-Type: $mime\r\n\r\n".toByteArray())
            out.write(data)
            out.write("\r\n".toByteArray())
        }

        fun build(): ByteArray {
            out.write("--$boundary--\r\n".toByteArray())
            return out.toByteArray()
        }
    }
}
