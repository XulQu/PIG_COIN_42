package com.bootybay.hallowmiss.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bootybay.hallowmiss.data.Db
import com.bootybay.hallowmiss.data.Design
import com.bootybay.hallowmiss.data.Events
import com.bootybay.hallowmiss.data.Prefs
import com.bootybay.hallowmiss.data.Status
import com.bootybay.hallowmiss.design.Ideas
import com.bootybay.hallowmiss.net.Etsy
import com.bootybay.hallowmiss.net.Http
import com.bootybay.hallowmiss.net.Printful
import com.bootybay.hallowmiss.work.Scheduler
import com.bootybay.hallowmiss.work.Studio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

private val TABS = listOf("Approvals", "Post", "Finances", "Stats", "R&D", "Ideas", "Settings")
private const val MUG_RATIO = 2700f / 1050f

@Composable
fun BootyBayApp() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val db = remember { Db.get(ctx) }
    val tick by Events.tick.collectAsState()
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Only ticks while the dashboard is on screen; it just refreshes "x min ago" labels.
    LaunchedEffect(Unit) { while (true) { delay(30_000); clock = System.currentTimeMillis() } }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (prefs.automate) Scheduler.go(ctx) // re-arm after an app update
    }

    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().imePadding()) {
        Header(prefs, db, tick, clock)
        ScrollableTabRow(selectedTabIndex = tab, containerColor = Color.Black, contentColor = Bone, edgePadding = 8.dp) {
            TABS.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 13.sp) })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> ApprovalsTab(db, tick)
                1 -> PostTab(db, tick)
                2 -> FinancesTab(db, tick)
                3 -> StatsTab(db, tick)
                4 -> ResearchTab(db, prefs, tick, clock)
                5 -> IdeasTab(db, prefs, tick)
                else -> SettingsTab(prefs, db, tick)
            }
        }
    }
}

// ---------------------------------------------------------------- header
@Composable
private fun Header(p: Prefs, db: Db, tick: Int, clock: Long) {
    val ctx = LocalContext.current
    val automate = remember(tick) { p.automate }
    val working = remember(tick, clock) { p.workingNow() }
    val counts = remember(tick) { db.counts() }
    val hb = remember(tick) { p.lastHeartbeat }
    val (label, color) = when {
        working.isNotEmpty() -> "WORKING" to Gold
        !automate -> "PAUSED" to Dim
        hb == 0L -> "STARTING" to Gold
        clock - hb < 20 * 60_000L -> "RUNNING" to Ok
        else -> "STALLED" to Warn
    }
    val nasa = if (hb == 0L) "NASA clock: not pinged yet"
    else "NASA ping ${ago(hb, clock)}" + (if (p.lastHeartbeatOk) " (clock drift ${"%.1f".format(p.nasaDriftMs / 1000.0)}s)" else " (failed)")
    val nextBatch = if (!automate) "" else {
        val due = p.lastBatch + p.batchHours * 3_600_000L - clock
        if (due <= 0) " · batch due" else " · next batch in ${due / 3_600_000}h${(due / 60_000) % 60}m"
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("BootyBay", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Bone, fontFamily = FontFamily.Serif)
                Text("Hallowmiss mug studio", color = Dim, fontSize = 12.sp)
            }
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { if (automate) Scheduler.pause(ctx) else Scheduler.go(ctx) },
                colors = ButtonDefaults.buttonColors(containerColor = if (automate) Blood else Pine),
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text(if (automate) "PAUSE" else "GO · AUTOMATE", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { Scheduler.makeNow(ctx, 10); toast(ctx, "Making 10 designs…") },
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("MAKE 10", fontWeight = FontWeight.Bold, color = Bone) }
        }
        Spacer(Modifier.height(6.dp))
        if (working.isNotEmpty()) Text(working, color = Gold, fontSize = 12.sp)
        Text("$nasa$nextBatch · ${counts[Status.PENDING] ?: 0} awaiting approval", color = Dim, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------- approvals
@Composable
private fun ApprovalsTab(db: Db, tick: Int) {
    val ctx = LocalContext.current
    val items = remember(tick) { db.designs(listOf(Status.PENDING)) }
    var full by remember { mutableStateOf<Design?>(null) }

    if (items.isEmpty()) {
        Empty("Nothing waiting. Tap MAKE 10, or GO to let the studio run on its own.")
    } else {
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${items.size} awaiting approval", color = Bone, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        items.forEach { db.setStatus(it.id, Status.APPROVED) }
                        Scheduler.post(ctx); Events.bump()
                    }) { Text("Approve all", color = Ok) }
                }
            }
            items(items, key = { it.id }) { d ->
                ApprovalCard(d, onOpen = { full = d }) { approve, feedback ->
                    if (approve) {
                        db.setStatus(d.id, Status.APPROVED, feedback = feedback)
                        Scheduler.post(ctx)
                    } else {
                        db.setStatus(d.id, Status.REJECTED, feedback = feedback)
                        Studio.deleteFiles(d)
                    }
                    Events.bump()
                }
            }
        }
    }

    full?.let { d ->
        Dialog(onDismissRequest = { full = null }) {
            Column(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(Panel).padding(10.dp)) {
                FileImage(d.png, Modifier.fillMaxWidth().aspectRatio(MUG_RATIO), sample = 2)
                Spacer(Modifier.height(8.dp))
                Text(d.title, color = Bone, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text("Tags: ${d.tags.replace(",", ", ")}", color = Dim, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Text("Sources (public domain):\n${d.credits}", color = Dim, fontSize = 11.sp)
                TextButton(onClick = { full = null }, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
}

@Composable
private fun ApprovalCard(d: Design, onOpen: () -> Unit, onDecide: (Boolean, String) -> Unit) {
    var fb by remember(d.id) { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(10.dp)) {
            FileImage(d.thumb, Modifier.fillMaxWidth().aspectRatio(MUG_RATIO).clickable { onOpen() })
            Spacer(Modifier.height(8.dp))
            Text(d.phrase, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Bone, fontFamily = FontFamily.Serif)
            Text("${d.motifH} × ${d.motifX} · ${d.palette} · tap image for full size", color = Dim, fontSize = 12.sp)
            OutlinedTextField(
                value = fb, onValueChange = { fb = it }, label = { Text("Feedback (optional)") },
                maxLines = 3, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDecide(true, fb.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = Pine),
                    modifier = Modifier.weight(1f)) { Text("Approve") }
                OutlinedButton(onClick = { onDecide(false, fb.trim()) }, modifier = Modifier.weight(1f)) { Text("Reject", color = Blood) }
            }
        }
    }
}

// ---------------------------------------------------------------- post
@Composable
private fun PostTab(db: Db, tick: Int) {
    val ctx = LocalContext.current
    val items = remember(tick) { db.designs(listOf(Status.APPROVED, Status.POSTING, Status.FAILED, Status.POSTED), 150) }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Approved → Printful → Etsy", color = Dim, modifier = Modifier.weight(1f), fontSize = 12.sp)
                Button(onClick = { Scheduler.post(ctx); toast(ctx, "Posting…") }) { Text("Post now") }
            }
        }
        if (items.isEmpty()) item { Text("Nothing approved yet.", color = Dim) }
        items(items, key = { it.id }) { d ->
            Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(Panel).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FileImage(d.thumb, Modifier.width(120.dp).aspectRatio(MUG_RATIO))
                Column(Modifier.weight(1f)) {
                    Text(d.phrase, color = Bone, fontWeight = FontWeight.Bold)
                    val c = when (d.status) { Status.POSTED -> Ok; Status.FAILED -> Blood; Status.POSTING -> Gold; else -> Dim }
                    Text(d.status + if (d.postedAt > 0) " · ${date(d.postedAt)}" else "", color = c, fontSize = 12.sp)
                    if (d.error.isNotBlank() && d.status == Status.FAILED) Text(d.error, color = Blood, fontSize = 11.sp, maxLines = 3)
                    Row {
                        if (d.listingId > 0) TextButton(onClick = { openUrl(ctx, "https://www.etsy.com/listing/${d.listingId}") }) { Text("Etsy") }
                        if (d.status == Status.FAILED) TextButton(onClick = {
                            db.setStatus(d.id, Status.APPROVED, error = ""); Scheduler.post(ctx); Events.bump()
                        }) { Text("Retry") }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- finances
@Composable
private fun FinancesTab(db: Db, tick: Int) {
    val sales = remember(tick) { db.sales() }
    val posted = remember(tick) { db.designs(listOf(Status.POSTED), 100_000) }
    val monthStart = remember {
        Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
    }

    @Composable
    fun block(title: String, since: Long) {
        val s = sales.filter { it.ts >= since }
        val revenue = s.sumOf { it.amount }
        val fees = s.sumOf { it.fees }
        val cost = s.sumOf { it.cost }
        val listing = posted.count { it.postedAt >= since } * 0.20
        val net = revenue - fees - cost - listing
        SectionTitle(title)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Revenue", money(revenue), Modifier.weight(1f))
            StatCard("Net profit", money(net), Modifier.weight(1f), if (net >= 0) Ok else Blood)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Printful", money(cost), Modifier.weight(1f))
            StatCard("Etsy fees*", money(fees), Modifier.weight(1f))
            StatCard("Listings", money(listing), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Orders", s.map { it.receiptId }.distinct().size.toString(), Modifier.weight(1f))
            StatCard("Profit / sale", if (s.isEmpty()) "–" else money((revenue - fees - cost) / s.size), Modifier.weight(1f))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        block("This month", monthStart)
        block("All time", 0L)
        Text("*Etsy fees estimated: 6.5% transaction + ~3% + \$0.25 processing + \$0.20 renewal per sale. Printful costs are the real order totals. Listings cost \$0.20 each.",
            color = Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        SectionTitle("Recent sales")
        if (sales.isEmpty()) Text("No sales yet. You'll get a notification the moment one lands.", color = Dim)
        sales.take(40).forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(s.title.take(50), color = Bone, fontSize = 13.sp, maxLines = 1)
                    Text("${date(s.ts)} · ${s.country}" + if (s.pfOrder.isNotBlank()) " · Printful #${s.pfOrder}" else " · not sent to Printful",
                        color = Dim, fontSize = 11.sp)
                }
                Text(money(s.amount), color = Ok)
            }
            HorizontalDivider(color = Color(0xFF1A1A1A))
        }
    }
}

// ---------------------------------------------------------------- stats
@Composable
private fun StatsTab(db: Db, tick: Int) {
    val counts = remember(tick) { db.counts() }
    val sales = remember(tick) { db.sales() }
    val stats = remember(tick) { db.motifStats() }
    val designs = remember(tick) { db.allDesigns().associateBy { it.id } }

    val approved = (counts[Status.APPROVED] ?: 0) + (counts[Status.POSTING] ?: 0) + (counts[Status.POSTED] ?: 0) + (counts[Status.FAILED] ?: 0)
    val rejected = counts[Status.REJECTED] ?: 0
    val made = counts.values.sum()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Designs made", made.toString(), Modifier.weight(1f))
            StatCard("Live", (counts[Status.POSTED] ?: 0).toString(), Modifier.weight(1f))
            StatCard("Items sold", sales.size.toString(), Modifier.weight(1f), Ok)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Approval rate", if (approved + rejected == 0) "–" else "${approved * 100 / (approved + rejected)}%", Modifier.weight(1f))
            val live = counts[Status.POSTED] ?: 0
            StatCard("Sales / listing", if (live == 0) "–" else "%.2f".format(sales.size.toDouble() / live), Modifier.weight(1f))
        }

        SectionTitle("Sales, last 30 days")
        val days = IntArray(30)
        val now = System.currentTimeMillis()
        sales.forEach { s -> val d = ((now - s.ts) / 86_400_000L).toInt(); if (d in 0..29) days[29 - d]++ }
        val max = (days.maxOrNull() ?: 0).coerceAtLeast(1)
        Canvas(Modifier.fillMaxWidth().height(110.dp).background(Panel).padding(6.dp)) {
            val bw = size.width / 30f
            days.forEachIndexed { i, v ->
                val h = size.height * v / max
                drawRect(if (v > 0) Blood else Color(0xFF222222), Offset(i * bw + 1f, size.height - h.coerceAtLeast(2f)), Size(bw - 2f, h.coerceAtLeast(2f)))
            }
        }
        Text("${days.sum()} sold in 30 days", color = Dim, fontSize = 11.sp)

        SectionTitle("Top designs")
        val top = sales.groupBy { it.designId }.entries.sortedByDescending { it.value.size }.take(8)
        if (top.isEmpty()) Text("No sales yet.", color = Dim)
        top.forEach { (id, list) ->
            Bar(designs[id]?.phrase ?: "Other shop item", list.size.toFloat() / top.first().value.size, "${list.size} sold", Ok)
        }

        SectionTitle("Motifs: made · approved · sold")
        stats.entries.sortedByDescending { it.value.made }.forEach { (m, s) ->
            val reviewed = s.approved + s.rejected
            val rate = if (reviewed == 0) "–" else "${s.approved * 100 / reviewed}%"
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(m, color = Bone, modifier = Modifier.weight(1f), fontSize = 13.sp)
                Text("${s.made}", color = Dim, modifier = Modifier.width(44.dp), fontSize = 13.sp)
                Text(rate, color = Dim, modifier = Modifier.width(52.dp), fontSize = 13.sp)
                Text("${s.sold}", color = if (s.sold > 0) Ok else Dim, modifier = Modifier.width(36.dp), fontSize = 13.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- R&D
@Composable
private fun ResearchTab(db: Db, p: Prefs, tick: Int, clock: Long) {
    val ctx = LocalContext.current
    val items = remember(tick) { db.latestResearch() }
    val trends = remember(tick) { p.trends().entries.sortedByDescending { it.value }.filter { it.value > 0 } }
    val terms = remember(tick) {
        try { val a = JSONArray(p.termsJson); (0 until a.length()).map { a.getJSONArray(it).let { t -> t.getString(0) to t.getInt(1) } } }
        catch (e: Exception) { emptyList() }
    }
    val prices = remember(tick) { try { JSONObject(p.pricesJson) } catch (e: Exception) { JSONObject() } }
    val feedback = remember(tick) { db.allDesigns().filter { it.feedback.isNotBlank() }.take(30) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Market scan ${ago(p.lastResearch, clock)} · runs daily", color = Dim, modifier = Modifier.weight(1f), fontSize = 12.sp)
            Button(onClick = { Scheduler.research(ctx); toast(ctx, "Researching…") }) { Text("Research now") }
        }
        Text("Reads the top ~100 Etsy results for Christmas, Halloween and creepy-Christmas mugs (and eBay when keys are set). Amazon has no open API for this, so it's skipped. BootyBay copies what sells (themes, keywords, price), never other sellers' artwork.",
            color = Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))

        SectionTitle("Median price")
        if (prices.length() == 0) Text("No data yet.", color = Dim)
        prices.keys().forEach { k -> Text("$k: ${money(prices.optDouble(k))}   (yours: \$${p.retailPrice})", color = Bone) }

        SectionTitle("Motifs in top-selling listings")
        if (trends.isEmpty()) Text("No data yet.", color = Dim)
        val tMax = trends.firstOrNull()?.value ?: 1.0
        trends.take(12).forEach { (m, share) -> Bar(m, (share / tMax).toFloat(), "${(share * 100).toInt()}%") }

        SectionTitle("Top keywords (trademarks filtered)")
        val kMax = terms.firstOrNull()?.second ?: 1
        terms.take(20).forEach { (t, n) -> Bar(t, n.toFloat() / kMax, n.toString(), Pine) }

        SectionTitle("Most-favourited competitor listings")
        items.take(25).forEach { r ->
            Text("${r.source.uppercase()} · ${money(r.price)} · ♥${r.favs}", color = Dim, fontSize = 11.sp)
            Text(r.title, color = Bone, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(bottom = 6.dp))
        }

        SectionTitle("Your feedback log")
        if (feedback.isEmpty()) Text("Feedback you leave on approvals shows up here.", color = Dim)
        feedback.forEach { d ->
            Text("${d.phrase} (${d.status.lowercase()}): ${d.feedback}", color = Bone, fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp))
        }
    }
}

// ---------------------------------------------------------------- ideas / suggestion box
@Composable
private fun IdeasTab(db: Db, p: Prefs, tick: Int) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    val suggestions = remember(tick) { db.suggestions() }
    val advice = remember(tick) { Ideas.advice(db, p) }
    val weights = remember(tick) { Ideas.weights(db, p).entries.sortedByDescending { it.value } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        SectionTitle("Suggestion box")
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            label = { Text("What should BootyBay focus on next?") },
            placeholder = { Text("e.g. more krampus · fewer owls · \"Skulls Roasting on an Open Fire\"") },
            modifier = Modifier.fillMaxWidth(), maxLines = 3,
        )
        Button(onClick = {
            val t = text.trim()
            when {
                t.isEmpty() -> {}
                Ideas.isTrademarked(t) -> toast(ctx, "That mentions a trademark. Etsy would take it down, so it wasn't added.")
                else -> { db.addSuggestion(t); text = ""; Events.bump() }
            }
        }, modifier = Modifier.padding(top = 6.dp)) { Text("Add") }
        Text("Motif words boost that motif (×3); 'less / fewer / no' suppresses it. Text in \"quotes\" becomes a mug phrase.",
            color = Dim, fontSize = 11.sp)

        suggestions.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.text, color = if (s.active) Bone else Dim, fontSize = 13.sp)
                    val m = Ideas.keywordsIn(s.text)
                    if (m.isNotEmpty()) Text((if (Ideas.isNegative(s.text)) "suppressing: " else "boosting: ") + m.joinToString(),
                        color = Dim, fontSize = 11.sp)
                }
                Switch(checked = s.active, onCheckedChange = { db.setSuggestionActive(s.id, it); Events.bump() })
                TextButton(onClick = { db.deleteSuggestion(s.id); Events.bump() }) { Text("✕", color = Dim) }
            }
        }

        SectionTitle("BootyBay suggests")
        advice.forEach { Text("• $it", color = Bone, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp)) }

        SectionTitle("Current focus (motif weights)")
        val wMax = weights.firstOrNull()?.value ?: 1.0
        weights.forEach { (m, w) -> Bar(m, (w / wMax).toFloat(), "%.1f×".format(w), Gold) }
    }
}

// ---------------------------------------------------------------- settings
@Composable
private fun SettingsTab(p: Prefs, db: Db, tick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    val logs = remember(tick) { db.logs() }
    val etsyConnected = remember(tick) { Etsy(p).connected }

    fun runBg(label: String, block: () -> String) {
        result = "$label…"
        scope.launch {
            result = withContext(Dispatchers.IO) { try { block() } catch (e: Exception) { "$label failed: ${e.message}" } }
            Events.bump()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        if (result.isNotEmpty()) Text(result, color = Gold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))

        SectionTitle("Automation")
        PrefField("Designs per batch", p.batchSize.toString(), number = true) { v -> v.toIntOrNull()?.let { p.batchSize = it.coerceIn(1, 30) } }
        PrefField("Hours between batches", p.batchHours.toString(), number = true) { v -> v.toIntOrNull()?.let { p.batchHours = it.coerceIn(1, 168) } }
        PrefField("Pause generating when this many await approval", p.pendingCap.toString(), number = true) { v -> v.toIntOrNull()?.let { p.pendingCap = it.coerceIn(5, 500) } }
        Toggle("Only make designs while charging (saves the most battery)", p.chargingOnly) { p.chargingOnly = it }

        SectionTitle("Printful")
        PrefField("Printful API token", p.printfulToken, secret = true) { p.printfulToken = it }
        PrefField("Store ID (a Manual/API store)", p.printfulStoreId, number = true) { p.printfulStoreId = it }
        OutlinedButton(onClick = {
            runBg("Finding stores") {
                val s = Printful(p).stores()
                (0 until s.length()).joinToString("\n") { s.getJSONObject(it).let { o -> "${o.optLong("id")}: ${o.optString("name")} (${o.optString("type")})" } }
                    .ifEmpty { "No stores. Create a 'Manual order platform / API' store in Printful." }
            }
        }) { Text("Find my stores") }
        PrefField("Retail price (USD)", p.retailPrice, number = true) { p.retailPrice = it }
        PrefField("Printful variant ID (1320 = 11oz white mug)", p.printfulVariantId, number = true) { p.printfulVariantId = it }
        PrefField("Printful product ID (19 = white glossy mug)", p.printfulProductId, number = true) { p.printfulProductId = it }
        Toggle("Auto-confirm Printful orders (charges your Printful card)", p.autoConfirmOrders) { p.autoConfirmOrders = it }
        PrefField("imgbb API key (free, hosts print files for Printful)", p.imgbbKey, secret = true) { p.imgbbKey = it }

        SectionTitle("Etsy")
        PrefField("Etsy keystring", p.etsyKey, secret = true) { p.etsyKey = it }
        PrefField("Etsy shared secret", p.etsySecret, secret = true) { p.etsySecret = it }
        Text(if (etsyConnected) "Connected · shop ${p.etsyShopId}" else "Not connected. Register redirect URI ${Etsy.REDIRECT} on your Etsy app first.",
            color = if (etsyConnected) Ok else Dim, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (p.etsyKey.isBlank()) toast(ctx, "Add the keystring first") else openUrl(ctx, Etsy(p).authUrl())
            }) { Text(if (etsyConnected) "Reconnect Etsy" else "Connect Etsy") }
            OutlinedButton(onClick = {
                p.etsyTaxonomyId = 0; p.etsyShippingProfileId = 0; p.etsyReadinessId = 0; toast(ctx, "Will re-detect on next post")
            }) { Text("Re-detect shop setup") }
        }
        OutlinedTextField(value = pasted, onValueChange = { pasted = it }, singleLine = true,
            label = { Text("If the browser didn't return here: paste the redirect URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = {
            val v = pasted
            runBg("Connecting Etsy") { Etsy(p).finishAuth(v); "Etsy connected · shop ${p.etsyShopId}" }
        }) { Text("Finish connecting") }
        Toggle("Publish listings live after approval (\$0.20 each). Off = Etsy drafts", p.etsyPublish) { p.etsyPublish = it }

        SectionTitle("eBay research (optional)")
        PrefField("eBay App ID (client id)", p.ebayClientId, secret = true) { p.ebayClientId = it }
        PrefField("eBay Cert ID (client secret)", p.ebayClientSecret, secret = true) { p.ebayClientSecret = it }

        SectionTitle("Check")
        OutlinedButton(onClick = {
            runBg("Testing") {
                val out = ArrayList<String>()
                out += try { val r = Http.request("https://www.nasa.gov/", "HEAD", timeoutMs = 15_000); "NASA clock: ${r.header("Date")}" } catch (e: Exception) { "NASA: ${e.message}" }
                out += if (p.printfulToken.isBlank()) "Printful: no token" else try { "Printful: ${Printful(p).stores().length()} store(s)" } catch (e: Exception) { "Printful: ${e.message}" }
                out += if (!Etsy(p).connected) "Etsy: not connected" else try { "Etsy: shop ${Etsy(p).me().optLong("shop_id")}" } catch (e: Exception) { "Etsy: ${e.message}" }
                out += if (p.imgbbKey.isBlank()) "imgbb: no key" else "imgbb: key set"
                out.joinToString("\n")
            }
        }) { Text("Test connections") }

        SectionTitle("Log")
        logs.forEach { l -> Text("${date(l.ts)}  ${l.msg}", color = Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 1.dp)) }
    }
}

@Composable
private fun Toggle(label: String, initial: Boolean, onChange: (Boolean) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Bone, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = v, onCheckedChange = { v = it; onChange(it) })
    }
}
