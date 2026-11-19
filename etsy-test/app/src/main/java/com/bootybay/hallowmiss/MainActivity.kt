package com.bootybay.hallowmiss

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.bootybay.hallowmiss.data.Db
import com.bootybay.hallowmiss.data.Events
import com.bootybay.hallowmiss.data.Prefs
import com.bootybay.hallowmiss.net.Etsy
import com.bootybay.hallowmiss.ui.BootyBayApp
import com.bootybay.hallowmiss.ui.BootyTheme
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent { BootyTheme { BootyBayApp() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Etsy OAuth redirect: bootybay://etsy-callback?code=...&state=... */
    private fun handle(i: Intent?) {
        val data = i?.data ?: return
        if (data.scheme != "bootybay") return
        val url = data.toString()
        thread {
            val p = Prefs(this)
            val db = Db.get(this)
            try {
                val state = data.getQueryParameter("state")
                if (state != null && state != p.etsyState) throw Exception("state mismatch, try Connect again")
                Etsy(p).finishAuth(url)
                db.log("Etsy connected (shop ${p.etsyShopId})")
            } catch (e: Exception) {
                db.log("Etsy connect failed: ${e.message}")
            }
            Events.bump()
        }
    }
}
