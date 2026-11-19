package au.com.bargainbloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.min
import kotlin.math.sqrt

data class VisualMatch(val deal: Deal, val similarity: Double)

/** Private, on-device approximate colour/pattern matching, not garment recognition. */
object VisualMatcher {
    suspend fun find(context: Context, photo: Uri, products: List<Deal>): List<VisualMatch> =
        withContext(Dispatchers.IO) {
            val reference = try {
                context.contentResolver.openInputStream(photo)?.use { bitmap(it) }?.let { features(it) }
            } catch (_: Exception) { null } ?: return@withContext emptyList()
            val candidates = products.filter { it.photo.startsWith("https://") && it.price > 0.0 }
                .distinctBy { it.id }.sortedWith(compareBy<Deal> { it.price }.thenByDescending { it.lastSeen })
                .take(64)
            val semaphore = Semaphore(5)
            coroutineScope {
                candidates.map { item ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val f = withTimeoutOrNull(9000L) { remoteFeatures(context, item.photo) }
                            f?.let { VisualMatch(item, cosine(reference, it)) }
                        }
                    }
                }.awaitAll().filterNotNull().sortedByDescending { it.similarity }.take(24)
            }
        }

    private fun bitmap(stream: java.io.InputStream): Bitmap? {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4; inPreferredConfig = Bitmap.Config.RGB_565 }
        return BitmapFactory.decodeStream(stream, null, opts)
    }

    private fun remoteFeatures(context: Context, photo: String): DoubleArray? {
        val cache = context.getSharedPreferences("bloom_visual_cache", Context.MODE_PRIVATE)
        val cached = cache.getString(photo, null)
        if (cached != null) {
            val items = cached.split(",").mapNotNull { it.toDoubleOrNull() }
            if (items.size == 32) return items.toDoubleArray()
        }
        val uri = Uri.parse(photo)
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return null
        val connection = URL(photo).openConnection() as? HttpURLConnection ?: return null
        connection.connectTimeout = 3500
        connection.readTimeout = 6000
        connection.instanceFollowRedirects = true
        return try {
            if (connection.responseCode != 200) return null
            val len = connection.contentLength
            if (len > 4_000_000) return null
            val original = connection.inputStream.use { bitmap(it) } ?: return null
            val result = features(original)
            cache.edit().putString(photo, result.joinToString(",") { String.format(Locale.US, "%.5f", it) }).apply()
            result
        } catch (_: Exception) { null } finally { connection.disconnect() }
    }

    private fun features(original: Bitmap): DoubleArray {
        val b = Bitmap.createScaledBitmap(original, 32, 32, true)
        val color = DoubleArray(32)
        val hsv = FloatArray(3)
        var prev = 0.0
        for (y in 0 until 32) for (x in 0 until 32) {
            val pixel = b.getPixel(x, y)
            android.graphics.Color.colorToHSV(pixel, hsv)
            val saturation = hsv[1]
            val value = hsv[2]
            val hue = (hsv[0] / 360.0).coerceIn(0.0, 0.999)
            val hueBin = (hue * 16).toInt()
            color[hueBin] += saturation * 0.65 + 0.05
            color[16 + (value * 7.99).toInt()] += 0.40
            color[24 + (saturation * 7.99).toInt()] += 0.28
            val grayscale = (android.graphics.Color.red(pixel) * 0.3 +
                android.graphics.Color.green(pixel) * 0.59 +
                android.graphics.Color.blue(pixel) * 0.11) / 255.0
            if (x > 0) {
                val edge = kotlin.math.abs(grayscale - prev)
                color[24 + min(7, (edge * 8).toInt())] += 0.04
            }
            prev = grayscale
        }
        if (b !== original) b.recycle()
        original.recycle()
        val norm = sqrt(color.sumOf { it * it }).takeIf { it > 0 } ?: 1.0
        return color.map { it / norm }.toDoubleArray()
    }

    private fun cosine(a: DoubleArray, b: DoubleArray): Double {
        if (a.size != b.size) return 0.0
        return a.indices.sumOf { a[it] * b[it] }.coerceIn(0.0, 1.0)
    }
}
