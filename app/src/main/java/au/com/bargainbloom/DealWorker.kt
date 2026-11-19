package au.com.bargainbloom

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class DealWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        val repo = DealStore(applicationContext)
        val result = repo.scan()
        val extras = BloomExtras(applicationContext)
        val targets = extras.dueAlerts(repo.deals())
        val wanted = (result.drops.filter { it.price <= repo.maxPrice() } + targets)
            .distinctBy { it.id }.sortedBy { it.price }
        if (wanted.isNotEmpty() && notifyDrops(wanted)) {
            extras.markAlerted(targets)
        }
        Result.success()
    } catch (_: Exception) { Result.retry() }

    private fun notifyDrops(drops: List<Deal>): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "price_drops"
        if (Build.VERSION.SDK_INT >= 26) mgr.createNotificationChannel(
            NotificationChannel(channelId, "Clothing price drops", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pending = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val best = drops.first()
        val title = if (drops.size == 1) "Price dropped: ${best.name}" else "${drops.size} clothing price drops"
        val body = if (drops.size == 1) "${best.store}: A$%.2f".format(best.price)
            else "Lowest now A$%.2f at ${best.store}".format(best.price)
        mgr.notify(1001, NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(body).setAutoCancel(true)
            .setContentIntent(pending).setPriority(NotificationCompat.PRIORITY_DEFAULT).build())
        return true
    }

    companion object {
        fun schedule(ctx: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val work = PeriodicWorkRequestBuilder<DealWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "bargain_bloom_shop_scan", ExistingPeriodicWorkPolicy.KEEP, work
            )
        }
    }
}
