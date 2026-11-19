package com.bootybay.hallowmiss.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bootybay.hallowmiss.MainActivity
import com.bootybay.hallowmiss.R
import com.bootybay.hallowmiss.data.Db
import com.bootybay.hallowmiss.data.Events
import com.bootybay.hallowmiss.data.Prefs
import com.bootybay.hallowmiss.data.Status
import com.bootybay.hallowmiss.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Power budget: no foreground service, no wakelocks. One periodic job every 15 minutes
 * (Android's minimum, batched by the OS with other apps' work) does a single HEAD request
 * to nasa.gov for the clock, checks Etsy for sales, and only *then* decides whether heavier
 * work (a design batch, research) is due. Heavy work waits for "battery not low".
 */
object Scheduler {
    const val HEART = "heartbeat"
    const val GEN = "generate"
    const val POST = "post"
    const val RESEARCH = "research"

    private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun go(ctx: Context) {
        Prefs(ctx).automate = true
        val req = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES).setConstraints(net).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(HEART, ExistingPeriodicWorkPolicy.UPDATE, req)
        Events.bump()
    }

    fun pause(ctx: Context) {
        val p = Prefs(ctx)
        p.automate = false
        val wm = WorkManager.getInstance(ctx)
        wm.cancelUniqueWork(HEART)
        wm.cancelUniqueWork(GEN)
        wm.cancelUniqueWork(RESEARCH)
        p.markWorking("")
    }

    fun makeNow(ctx: Context, count: Int) {
        val req = OneTimeWorkRequestBuilder<GenerateWorker>().setInputData(workDataOf("count" to count)).setConstraints(net).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(GEN, ExistingWorkPolicy.KEEP, req)
    }

    fun autoBatch(ctx: Context, p: Prefs) {
        val c = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(p.chargingOnly)
            .build()
        val req = OneTimeWorkRequestBuilder<GenerateWorker>().setInputData(workDataOf("count" to p.batchSize)).setConstraints(c).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(GEN, ExistingWorkPolicy.KEEP, req)
    }

    fun post(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<PostWorker>().setConstraints(net).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(POST, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    fun research(ctx: Context) {
        val c = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
        val req = OneTimeWorkRequestBuilder<ResearchWorker>().setConstraints(c).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(RESEARCH, ExistingWorkPolicy.KEEP, req)
    }
}

object Notify {
    private const val SALES = "sales"
    private const val BATCH = "batch"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(SALES, "Sales", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(BATCH, "New designs", NotificationManager.IMPORTANCE_LOW))
    }

    private fun send(ctx: Context, channel: String, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify((System.currentTimeMillis() % 100_000).toInt(), n)
    }

    fun sale(ctx: Context, title: String, text: String) = send(ctx, SALES, title, text)
    fun batch(ctx: Context, title: String, text: String) = send(ctx, BATCH, title, text)
}

class HeartbeatWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val p = Prefs(ctx)
        val db = Db.get(ctx)
        if (!p.automate) return@withContext Result.success()

        // 1) NASA clock ping: a single HEAD request, read the server's Date header.
        try {
            val before = System.currentTimeMillis()
            val r = Http.request("https://www.nasa.gov/", "HEAD", timeoutMs = 15_000)
            val date = r.header("Date") ?: throw Exception("no Date header")
            val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }
            val nasa = fmt.parse(date)?.time ?: before
            p.lastNasaTime = date
            p.nasaDriftMs = (before + System.currentTimeMillis()) / 2 - nasa
            p.lastHeartbeatOk = true
        } catch (e: Exception) {
            p.lastHeartbeatOk = false
            db.log("NASA ping failed: ${e.message}")
        }
        p.lastHeartbeat = System.currentTimeMillis()

        // 2) Sales.
        try { SalesSync.run(ctx, p, db) } catch (e: Exception) { db.log("Sales check failed: ${e.message}") }

        // 3) Decide on heavier work.
        val now = System.currentTimeMillis()
        if (now - p.lastResearch > 24 * 3_600_000L) Scheduler.research(ctx)
        val pending = db.counts()[Status.PENDING] ?: 0
        if (now - p.lastBatch > p.batchHours * 3_600_000L && pending < p.pendingCap) Scheduler.autoBatch(ctx, p)
        if ((db.counts()[Status.APPROVED] ?: 0) > 0) Scheduler.post(ctx)

        Events.bump()
        Result.success()
    }
}

class GenerateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val p = Prefs(ctx)
        val db = Db.get(ctx)
        val n = inputData.getInt("count", p.batchSize)
        val studio = Studio(ctx, p, db)
        var made = 0
        try {
            for (i in 1..n) {
                if (isStopped) break
                p.markWorking("Making design $i of $n")
                try { if (studio.makeOne() != null) made++ } catch (e: Exception) { db.log("Design failed: ${e.message}") }
                Events.bump()
            }
        } finally {
            p.markWorking("")
            p.lastBatch = System.currentTimeMillis()
        }
        db.log("Batch finished: $made of $n designs made")
        if (made > 0) Notify.batch(ctx, "$made new Hallowmiss mugs", "Waiting for your approval in BootyBay")
        Events.bump()
        Result.success()
    }
}

class PostWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val p = Prefs(applicationContext)
        val db = Db.get(applicationContext)
        val poster = Poster(p, db)
        for (d in db.designs(listOf(Status.APPROVED)).reversed()) {
            if (isStopped) break
            db.setStatus(d.id, Status.POSTING); Events.bump()
            try {
                poster.post(db.design(d.id) ?: continue)
            } catch (e: Exception) {
                db.setStatus(d.id, Status.FAILED, error = e.message ?: e.javaClass.simpleName)
                db.log("Post #${d.id} failed: ${e.message}")
            }
            Events.bump()
        }
        Result.success()
    }
}

class ResearchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val p = Prefs(applicationContext)
        val db = Db.get(applicationContext)
        p.markWorking("Researching top mugs")
        try {
            val msg = Research.run(p, db)
            db.log("Research: $msg")
        } catch (e: Exception) {
            db.log("Research failed: ${e.message}")
        } finally {
            p.lastResearch = System.currentTimeMillis()
            p.markWorking("")
        }
        Result.success()
    }
}
