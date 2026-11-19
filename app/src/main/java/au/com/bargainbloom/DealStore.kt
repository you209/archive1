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
data class Shop(
    val name: String,
    val baseUrl: String,
    val enabled: Boolean = true,
    val mode: String = "FEED",
    val browseUrl: String = baseUrl
) {
    val isFeed: Boolean get() = mode == "FEED"
}

data class DealVariant(val size: String, val colour: String, val price: Double, val photo: String = "") {
    fun toJson() = JSONObject().put("size", size).put("colour", colour).put("price", price).put("photo", photo)
    companion object {
        fun fromJson(o: JSONObject) = DealVariant(o.optString("size"), o.optString("colour"), o.optDouble("price"), o.optString("photo"))
    }
}

data class DealMatch(val deal: Deal, val price: Double, val photo: String)
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
    val lastSeen: Long,
    val clothingType: String = "Other",
    val variants: List<DealVariant> = emptyList()
) {
    fun matching(size: String, colour: String): DealMatch? {
        if (size == "Any" && colour == "Any") return DealMatch(this, price, photo)
        val inStock = variants.filter {
            (size == "Any" || it.size == size) &&
            (colour == "Any" || it.colour == colour)
        }
        val cheapest = inStock.minByOrNull { it.price } ?: return null
        return DealMatch(this, cheapest.price, cheapest.photo.ifBlank { photo })
    }

    val discount: Int get() = if (comparePrice != null && comparePrice > price && comparePrice > 0.0)
        ((comparePrice - price) * 100 / comparePrice).toInt() else 0
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("store", store); put("link", link)
        put("photo", photo); put("price", price); put("comparePrice", comparePrice ?: JSONObject.NULL)
        put("sizes", availableSizes); put("category", category); put("tags", tags); put("lastSeen", lastSeen)
        put("clothingType", clothingType)
        put("variants", JSONArray().apply { variants.forEach { put(it.toJson()) } })
    }
    companion object {
        fun fromJson(o: JSONObject) = Deal(
            id = o.optString("id"), name = o.optString("name"), store = o.optString("store"),
            link = o.optString("link"), photo = o.optString("photo"), price = o.optDouble("price"),
            comparePrice = if (o.isNull("comparePrice")) null else o.optDouble("comparePrice"),
            availableSizes = o.optString("sizes"), category = o.optString("category"),
            tags = o.optString("tags"), lastSeen = o.optLong("lastSeen"),
            clothingType = o.optString("clothingType", "Other"),
            variants = o.optJSONArray("variants")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(DealVariant::fromJson) }
            } ?: emptyList()
        )
    }
}

data class ScanReport(val scanned: Int, val deals: Int, val drops: List<Deal>, val errors: List<String>)

class DealStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bargain_bloom", Context.MODE_PRIVATE)
    private val lock = Mutex()


    private fun defaults() = listOf(
        Shop("Dangerfield", "https://dangerfield.com.au", browseUrl = "https://dangerfield.com.au/collections/outlet"),
        Shop("Princess Highway", "https://princesshighway.com.au", browseUrl = "https://princesshighway.com.au/collections/final-outlet-markdowns"),
        Shop("Beserk", "https://www.beserk.com.au", browseUrl = "https://www.beserk.com.au/collections/sale-clothing"),
        Shop("Kmart", "https://www.kmart.com.au", mode = "BROWSE", browseUrl = "https://www.kmart.com.au/category/women/clearance-womens-clothing/"),
        Shop("BIG W", "https://www.bigw.com.au", mode = "BROWSE", browseUrl = "https://www.bigw.com.au/collection/womens-clothing-clearance"),
        Shop("Harris Scarfe", "https://www.harrisscarfe.com.au", mode = "BROWSE", browseUrl = "https://www.harrisscarfe.com.au/en-au/women/womens-clothing/clearance"),
        Shop("Target", "https://www.target.com.au", mode = "BROWSE", browseUrl = "https://www.target.com.au/collection/womens-sale"),
        Shop("Cotton On", "https://cottonon.com", mode = "BROWSE", browseUrl = "https://cottonon.com/AU/co/co-sale/sale-womens/"),
        Shop("Sportsgirl", "https://www.sportsgirl.com.au", mode = "BROWSE", browseUrl = "https://www.sportsgirl.com.au/sale/sale-clothing"),
        Shop("Glassons", "https://www.glassons.com", mode = "BROWSE", browseUrl = "https://www.glassons.com/c/sale"),
        Shop("Supre", "https://supre.com.au", mode = "BROWSE", browseUrl = "https://supre.com.au/sale/"),
        Shop("Dotti", "https://dotti.jgl.com.au", mode = "BROWSE", browseUrl = "https://dotti.jgl.com.au/shop/do-womens-sale-all")
    )

    fun shops(): List<Shop> {
        val defaults = defaults()
        val saved = prefs.getString("shops", null) ?: return defaults
        val restored = try {
            val a = JSONArray(saved)
            (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
                val url = o.getString("url")
                val builtin = defaults.find { it.baseUrl == url }
                Shop(
                    o.getString("name"), url, o.optBoolean("enabled", true),
                    o.optString("mode", builtin?.mode ?: "FEED"),
                    o.optString("browse", builtin?.browseUrl ?: url)
                )
            } }
        } catch (_: Exception) { defaults }
        // One-time migration for customers updating from v0.1; existing selections survive.
        if (!prefs.getBoolean("catalog_v2", false)) {
            val merged = restored + defaults.filterNot { def -> restored.any { it.baseUrl == def.baseUrl } }
            prefs.edit().putBoolean("catalog_v2", true).apply()
            saveShops(merged)
            return merged
        }
        return restored
    }

    fun saveShops(shops: List<Shop>) {
        val a = JSONArray()
        shops.forEach {
            a.put(JSONObject().put("name", it.name).put("url", it.baseUrl).put("enabled", it.enabled)
                .put("mode", it.mode).put("browse", it.browseUrl))
        }
        prefs.edit().putString("shops", a.toString()).apply()
    }

    fun addShop(name: String, address: String): Boolean {
        val url = try { URL(address.trim()) } catch (_: Exception) { return false }
        if (url.protocol != "https" || url.host.isBlank() || url.userInfo != null || url.port != -1) return false
        val canonical = "https://${url.host.lowercase(Locale.ROOT)}"
        val current = shops()
        if (current.any { it.baseUrl == canonical }) return false
        saveShops(current + Shop(name.take(40).ifBlank { url.host }, canonical, mode = "BROWSE", browseUrl = canonical))
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
            for (shop in shops().filter { it.enabled && it.isFeed }) {
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

        val options = p.optJSONArray("options")
        val optionNames = if (options == null) emptyList() else
            (0 until options.length()).map { options.optJSONObject(it)?.optString("name")?.lowercase(Locale.ROOT).orEmpty() }
        val sizeOptionIndex = optionNames.indexOfFirst { it.contains("size") }
        val colourOptionIndex = optionNames.indexOfFirst { it.contains("color") || it.contains("colour") }
        val colourFromTitle = identifyColour(title)
        val variants = p.optJSONArray("variants") ?: return null
        var minPrice = Double.MAX_VALUE
        var compareAt: Double? = null
        val sizes = mutableListOf<String>()
        val stockedVariants = mutableListOf<DealVariant>()
        for (i in 0 until variants.length()) {
            val v = variants.optJSONObject(i) ?: continue
            if (!v.optBoolean("available", false)) continue
            val price = v.optString("price").toDoubleOrNull() ?: continue
            if (price <= 0) continue
            val titleVariant = v.optString("public_title", v.optString("title"))
            val sizeRaw = if (sizeOptionIndex in 0..2) v.optString("option${sizeOptionIndex+1}") else titleVariant
            val colourRaw = if (colourOptionIndex in 0..2) v.optString("option${colourOptionIndex+1}") else titleVariant
            val size = identifySize(sizeRaw)
            val colour = identifyColour(colourRaw).ifBlank { colourFromTitle }
            if (size.isNotBlank()) sizes += size
            val variantPhoto = v.optJSONObject("featured_image")?.optString("src").orEmpty()
            stockedVariants += DealVariant(size, colour, price, variantPhoto)
            if (price < minPrice) {
                minPrice = price
                compareAt = v.optString("compare_at_price").toDoubleOrNull()?.takeIf { it > price }
            }
        }
        if (stockedVariants.isEmpty() || minPrice == Double.MAX_VALUE) return null
        val photo = p.optJSONArray("images")?.optJSONObject(0)?.optString("src").orEmpty()
            .ifBlank { p.optJSONObject("image")?.optString("src").orEmpty() }
        val category = when {
            listOf("goth", "punk", "grunge", "fishnet", "alt", "metal").any { all.contains(it) } -> "Goth / alt"
            listOf("lace", "ruffle", "floral", "bow", "frill", "coquette").any { all.contains(it) } -> "Coquette"
            listOf("pink", "y2k", "denim", "baby tee", "mini skirt").any { all.contains(it) } -> "Y2K"
            listOf("pyjama", "lounge", "sleep", "comfy", "robe").any { all.contains(it) } -> "Lounge"
            else -> "Other"
        }
        val clothingType = identifyType("$kind $title")
        return Deal(
            id = "${shop.baseUrl}|$id", name = title, store = shop.name,
            link = "${shop.baseUrl}/products/$handle", photo = photo, price = minPrice,
            comparePrice = compareAt, availableSizes = sizes.distinct().take(12).joinToString(", "),
            category = category, tags = tags, lastSeen = time,
            clothingType = clothingType, variants = stockedVariants
        )
    }
    private fun identifySize(raw: String): String {
        val text = raw.uppercase(Locale.ROOT).trim()
        if (text.isBlank() || text == "DEFAULT TITLE") return ""
        Regex("(?<![0-9])(?:AU\\s*)?(4|6|8|10|12|14|16|18|20|22|24|26)(?![0-9])").find(text)?.let { return it.groupValues[1] }
        Regex("(?<![A-Z])(XXXL|XXL|XL|XS|XXS|3XL|2XL|S|M|L)(?![A-Z])").find(text)?.let {
            return when (it.value) { "2XL" -> "XXL"; "3XL" -> "XXXL"; else -> it.value }
        }
        return ""
    }

    private fun identifyColour(raw: String): String {
        val text = raw.lowercase(Locale.ROOT)
        val names = listOf("Black", "White", "Pink", "Red", "Purple", "Blue", "Green", "Brown", "Cream", "Beige", "Grey", "Yellow", "Orange", "Navy", "Multi")
        val aliases = mapOf("Grey" to listOf("gray", "grey", "charcoal"), "Cream" to listOf("ivory", "cream"), "Pink" to listOf("pink", "rose", "blush", "magenta"), "Purple" to listOf("purple", "lilac", "lavender"), "Brown" to listOf("brown", "chocolate", "tan"), "Blue" to listOf("blue", "denim", "cobalt"), "Green" to listOf("green", "olive", "sage"), "Multi" to listOf("multicolour", "multi colour", "multicolor", "rainbow"), "Navy" to listOf("navy"))
        return names.firstOrNull { name ->
            (aliases[name] ?: listOf(name.lowercase(Locale.ROOT))).any { colour ->
                Regex("(?<![a-z])${Regex.escape(colour)}(?![a-z])").containsMatchIn(text)
            }
        }.orEmpty()
    }

    private fun identifyType(raw: String): String {
        val t = raw.lowercase(Locale.ROOT)
        return when {
            listOf("bralette", "bra", "panty", "panties", "knicker", "underwear", "brief", "lingerie", "corset", "garter").any { t.contains(it) } -> "Underwear"
            listOf("stocking", "tights", "sock", "fishnet", "leg warmer").any { t.contains(it) } -> "Stockings"
            listOf("dress", "playsuit", "jumpsuit").any { t.contains(it) } -> "Dresses"
            t.contains("skirt") -> "Skirts"
            listOf("shorts", "short ").any { t.contains(it) } -> "Shorts"
            listOf("jean", "pant", "trouser", "legging").any { t.contains(it) } -> "Pants"
            listOf("pyjama", "pajama", "sleep", "lounge", "robe").any { t.contains(it) } -> "Sleepwear"
            listOf("coat", "jacket", "cardigan", "hoodie", "jumper", "sweater", "fleece").any { t.contains(it) } -> "Outerwear"
            listOf("shoes", "boot", "sneaker", "heel", "slipper").any { t.contains(it) } -> "Shoes"
            listOf("blouse", "shirt", "cami", "tank", "tee", "crop", "top", "vest", "bodysuit").any { t.contains(it) } -> "Tops"
            else -> "Other"
        }
    }

}
