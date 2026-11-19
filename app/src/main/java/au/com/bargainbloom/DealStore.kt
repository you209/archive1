package au.com.bargainbloom

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Only uses publicly accessible Shopify product JSON feeds; no logins, anti-bot bypass or proxy. */
data class Shop(val name: String, val baseUrl: String, val enabled: Boolean = true)
data class Deal(
    val id: String,
    val name: String,
    val store: String,
    val link: String,
    val photo: String,
    val price: Double,
    val comparePrice: Double?,
    val availableSizes: String,
    val category: String,
    val tags: String,
    val lastSeen: Long
) {
    val discount: Int get() = if (comparePrice != null && comparePrice > price && comparePrice > 0.0)
        ((comparePrice - price) * 100 / comparePrice).toInt() else 0
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("store", store); put("link", link)
        put("photo", photo); put("price", price); put("comparePrice", comparePrice ?: JSONObject.NULL)
        put("sizes", availableSizes); put("category", category); put("tags", tags); put("lastSeen", lastSeen)
    }
    companion object {
        fun fromJson(o: JSONObject) = Deal(
            id = o.optString("id"), name = o.optString("name"), store = o.optString("store"),
            link = o.optString("link"), photo = o.optString("photo"), price = o.optDouble("price"),
            comparePrice = if (o.isNull("comparePrice")) null else o.optDouble("comparePrice"),
            availableSizes = o.optString("sizes"), category = o.optString("category"),
            tags = o.optString("tags"), lastSeen = o.optLong("lastSeen")
        )
    }
}

data class ScanReport(val scanned: Int, val deals: Int, val drops: List<Deal>, val errors: List<String>)

class DealStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bargain_bloom", Context.MODE_PRIVATE)
    private val lock = Mutex()

    fun shops(): List<Shop> {
        val saved = prefs.getString("shops", null) ?: return listOf(
            Shop("Dangerfield", "https://dangerfield.com.au"),
            Shop("Princess Highway", "https://princesshighway.com.au"),
            Shop("Beserk", "https://www.beserk.com.au")
        )
        return try {
            val a = JSONArray(saved)
            (0 until a.length()).map { i -> a.getJSONObject(i).let {
                Shop(it.getString("name"), it.getString("url"), it.optBoolean("enabled", true))
            } }
        } catch (_: Exception) { emptyList() }
    }

    fun saveShops(shops: List<Shop>) {
        val a = JSONArray()
        shops.forEach { a.put(JSONObject().put("name", it.name).put("url", it.baseUrl).put("enabled", it.enabled)) }
        prefs.edit().putString("shops", a.toString()).apply()
    }

    fun addShop(name: String, address: String): Boolean {
        val url = try { URL(address.trim()) } catch (_: Exception) { return false }
        if (url.protocol != "https" || url.host.isBlank() || url.userInfo != null || url.port != -1) return false
        val canonical = "https://${url.host.lowercase(Locale.ROOT)}"
        val current = shops()
        if (current.any { it.baseUrl == canonical }) return false
        saveShops(current + Shop(name.take(40).ifBlank { url.host }, canonical))
        return true
    }

    fun removeShop(url: String) { saveShops(shops().filterNot { it.baseUrl == url }) }
    fun toggleShop(url: String) { saveShops(shops().map { if (it.baseUrl == url) it.copy(enabled = !it.enabled) else it }) }

    fun deals(): List<Deal> = try {
        val a = JSONArray(prefs.getString("deals", "[]"))
        (0 until a.length()).map { Deal.fromJson(a.getJSONObject(it)) }
    } catch (_: Exception) { emptyList() }

    fun favourites(): Set<String> = prefs.getStringSet("favourites", emptySet())?.toSet() ?: emptySet()
    fun toggleFavourite(id: String) {
        val f = favourites().toMutableSet()
        if (!f.add(id)) f.remove(id)
        prefs.edit().putStringSet("favourites", f).apply()
    }

    fun maxPrice(): Int = prefs.getInt("max_price", 20)
    fun setMaxPrice(v: Int) { prefs.edit().putInt("max_price", v).apply() }
    fun lastScan(): Long = prefs.getLong("last_scan", 0)
    fun lastErrors(): List<String> = prefs.getString("last_errors", "")?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    fun recentPrices(id: String): List<Double> {
        return try {
            val a = JSONObject(prefs.getString("history", "{}") ?: "{}").optJSONArray(id)
            if (a == null) emptyList() else (0 until a.length()).map { a.getDouble(it) }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun scan(onProgress: (String) -> Unit = {}): ScanReport = withContext(Dispatchers.IO) {
        lock.withLock {
            val old = deals().associateBy { it.id }
            val collected = old.toMutableMap()
            val history = try { JSONObject(prefs.getString("history", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
            val errors = mutableListOf<String>()
            val drops = mutableListOf<Deal>()
            var scanned = 0
            val now = System.currentTimeMillis()
            for (shop in shops().filter { it.enabled }) {
                onProgress("Checking ${shop.name}…")
                val shopDeals = mutableMapOf<String, Deal>()
                var shopFailed = false
                for (page in 1..2) {
                    try {
                        val response = getJson("${shop.baseUrl}/products.json?limit=100&page=$page")
                        val products = response.optJSONArray("products") ?: throw IllegalStateException("No public product feed")
                        scanned += products.length()
                        for (i in 0 until products.length()) {
                            parseProduct(products.getJSONObject(i), shop, now)?.let { shopDeals[it.id] = it }
                        }
                        if (products.length() < 100) break
                        Thread.sleep(750L)
                    } catch (e: Exception) {
                        errors += "${shop.name}: ${e.message ?: "Feed unavailable"}"
                        shopFailed = true
                        break
                    }
                }
                for (deal in shopDeals.values) {
                    val prev = old[deal.id]
                    if (prev != null && deal.price < prev.price - 0.001) drops += deal
                    val priceSeries = history.optJSONArray(deal.id) ?: JSONArray()
                    if (priceSeries.length() == 0 || priceSeries.optDouble(priceSeries.length() - 1) != deal.price) {
                        val updated = JSONArray()
                        for (j in maxOf(0, priceSeries.length() - 19) until priceSeries.length()) updated.put(priceSeries.optDouble(j))
                        updated.put(deal.price)
                        history.put(deal.id, updated)
                    }
                    collected[deal.id] = deal
                }
                if (!shopFailed) {
                    val freshIds = shopDeals.keys
                    collected.entries.removeAll { it.value.store == shop.name && it.key !in freshIds && it.value.lastSeen < now - 30L * 86400_000L }
                }
            }
            val fav = favourites()
            val sorted = collected.values.sortedWith(compareBy<Deal> { it.price }.thenBy { it.name })
                .filter { it.price <= 150.0 || it.id in fav }
                .take(2000)
            val json = JSONArray().apply { sorted.forEach { put(it.toJson()) } }
            prefs.edit().putString("deals", json.toString()).putString("history", history.toString())
                .putLong("last_scan", now).putString("last_errors", errors.joinToString("\n")).apply()
            ScanReport(scanned, sorted.size, drops, errors)
        }
    }

    private fun getJson(address: String): JSONObject {
        val url = URL(address)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "BargainBloom/0.1 (personal clothing price watch)")
        return try {
            val code = connection.responseCode
            if (code != 200) throw IllegalStateException("HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } finally { connection.disconnect() }
    }

    private fun parseProduct(p: JSONObject, shop: Shop, time: Long): Deal? {
        val title = p.optString("title").trim()
        val handle = p.optString("handle")
        val kind = p.optString("product_type")
        val tags = p.optString("tags")
        val id = p.optString("id")
        if (title.isBlank() || handle.isBlank() || id.isBlank()) return null
        val all = "$title $kind $tags".lowercase(Locale.ROOT)
        val wearable = listOf("dress", "skirt", "top", "shirt", "cami", "blouse", "bralette", "bra", "lingerie", "knicker", "panty", "panties", "sock", "stocking", "tights", "corset", "cardigan", "sweater", "jumper", "pant", "short", "bodysuit", "lace", "hoodie", "crop", "jacket", "sleepwear", "pyjama", "fashion", "clothing", "goth", "vest", "leg warmer", "garter").any { all.contains(it) }
        if (!wearable || all.contains("gift card")) return null
        val variants = p.optJSONArray("variants") ?: return null
        var minPrice = Double.MAX_VALUE
        var compareAt: Double? = null
        val sizes = mutableListOf<String>()
        var available = false
        for (i in 0 until variants.length()) {
            val v = variants.optJSONObject(i) ?: continue
            val inStock = v.optBoolean("available", true)
            if (!inStock) continue
            available = true
            val price = v.optString("price").toDoubleOrNull() ?: continue
            val variantSize = v.optString("public_title", v.optString("title"))
            if (variantSize.isNotBlank() && variantSize != "Default Title") sizes += variantSize
            if (price < minPrice) {
                minPrice = price
                compareAt = v.optString("compare_at_price").toDoubleOrNull()?.takeIf { it > price }
            }
        }
        if (!available || minPrice == Double.MAX_VALUE || minPrice <= 0.0) return null
        val photo = p.optJSONArray("images")?.optJSONObject(0)?.optString("src").orEmpty()
            .ifBlank { p.optJSONObject("image")?.optString("src").orEmpty() }
        val category = when {
            listOf("goth", "punk", "black", "grunge", "fishnet", "alt", "metal").any { all.contains(it) } -> "Goth / alt"
            listOf("lace", "ruffle", "floral", "bow", "frill", "coquette").any { all.contains(it) } -> "Coquette"
            listOf("pink", "y2k", "denim", "baby tee", "mini skirt").any { all.contains(it) } -> "Y2K"
            listOf("pyjama", "lounge", "sleep", "comfy", "robe").any { all.contains(it) } -> "Lounge"
            else -> "Other"
        }
        return Deal(
            id = "${shop.baseUrl}|$id", name = title, store = shop.name,
            link = "${shop.baseUrl}/products/$handle", photo = photo, price = minPrice,
            comparePrice = compareAt, availableSizes = sizes.distinct().take(12).joinToString(", "),
            category = category, tags = tags, lastSeen = time
        )
    }
}
