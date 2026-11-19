package au.com.bargainbloom

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.util.Locale
import kotlin.math.roundToInt

data class FitProfile(
    val heightCm: Int = 173,
    val waistInches: Int = 32,
    val topSizes: String = "M,L",
    val bottomSizes: String = "",
    val underwearSizes: String = "",
    val braSizes: String = ""
)
data class ShippingEstimate(val subtotal: Double, val delivery: Double?, val unknownStores: List<String>) {
    val total: Double? get() = delivery?.let { subtotal + it }
}
data class OutfitIdea(val pieces: List<Deal>, val score: Double, val shipping: ShippingEstimate) {
    val subtotal: Double get() = pieces.sumOf { it.price }
}

class BloomExtras(context: Context) {
    private val pref = context.applicationContext.getSharedPreferences("bargain_bloom_v3", Context.MODE_PRIVATE)
    companion object {
        val collections = listOf("Wishlist", "Cute lounge", "Y2K pink", "Gothic", "Coquette", "Underwear")
        private const val WEEK = 7L * 24L * 60L * 60L * 1000L
    }

    private fun obj(key: String): JSONObject = try {
        JSONObject(pref.getString(key, "{}") ?: "{}")
    } catch (_: Exception) { JSONObject() }
    private fun write(key: String, data: JSONObject) { pref.edit().putString(key, data.toString()).apply() }

    @Synchronized
    fun recordScan(deals: List<Deal>, drops: List<Deal>, now: Long) {
        val seen = pref.getStringSet("seen_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        val newly = obj("newly")
        val dropped = obj("dropped")
        for (deal in deals) {
            if (seen.add(deal.id)) newly.put(deal.id, now)
        }
        drops.forEach { dropped.put(it.id, now) }
        for (o in listOf(newly, dropped)) {
            val keys = o.keys().asSequence().toList()
            for (id in keys) if (o.optLong(id) < now - WEEK) o.remove(id)
        }
        pref.edit().putStringSet("seen_ids", seen).putString("newly", newly.toString())
            .putString("dropped", dropped.toString()).apply()
    }

    fun newlyAdded(id: String): Boolean = obj("newly").optLong(id) > System.currentTimeMillis() - WEEK
    fun recentlyDropped(id: String): Boolean = obj("dropped").optLong(id) > System.currentTimeMillis() - WEEK

    fun collection(id: String): String = obj("collections").optString(id, "")
    fun saveCollection(id: String, collection: String) {
        val o = obj("collections")
        if (collection.isBlank()) o.remove(id) else o.put(id, collection)
        write("collections", o)
    }

    fun targetPrice(id: String): Double? {
        val o = obj("targets")
        return if (o.has(id)) o.optDouble(id).takeIf { !it.isNaN() && it > 0.0 } else null
    }
    fun setTargetPrice(id: String, value: Double?) {
        val o = obj("targets")
        if (value == null || !value.isFinite() || value <= 0.0) o.remove(id)
        else o.put(id, value)
        write("targets", o)
    }
    @Synchronized
    fun dueAlerts(deals: List<Deal>): List<Deal> {
        val targets = obj("targets")
        val notified = obj("notified")
        return deals.filter { d ->
            if (!targets.has(d.id)) false else {
                val target = targets.optDouble(d.id, -1.0)
                val last = notified.optDouble(d.id, Double.MAX_VALUE)
                d.price <= target && d.price < last - 0.001
            }
        }
    }
    @Synchronized
    fun markAlerted(deals: List<Deal>) {
        val notified = obj("notified")
        deals.forEach { notified.put(it.id, it.price) }
        write("notified", notified)
    }

    fun profile(): FitProfile {
        val p = obj("profile")
        return FitProfile(
            p.optInt("height", 173), p.optInt("waist", 32),
            p.optString("tops", "M,L"), p.optString("bottoms", ""),
            p.optString("underwear", ""), p.optString("bras", "")
        )
    }
    fun saveProfile(p: FitProfile) = write("profile", JSONObject()
        .put("height", p.heightCm).put("waist", p.waistInches)
        .put("tops", p.topSizes).put("bottoms", p.bottomSizes)
        .put("underwear", p.underwearSizes).put("bras", p.braSizes))

    private fun preferredSizes(d: Deal): Set<String> {
        val p = profile()
        val raw = when (d.clothingType) {
            "Tops", "Outerwear", "Dresses", "Sleepwear" -> p.topSizes
            "Skirts", "Shorts", "Pants" -> p.bottomSizes
            "Underwear" -> if (listOf("bra", "bralette", "bustier").any { d.name.contains(it, true) }) p.braSizes else p.underwearSizes
            else -> ""
        }
        return raw.split(",", ";", "/", " ").map { it.uppercase(Locale.ROOT).trim() }.filter { it.isNotBlank() }.toSet()
    }

    /** Unknown size is not a negative fit assertion; retailer charts must still be checked. */
    fun fitStatus(d: Deal): String {
        val sizes = preferredSizes(d)
        if (sizes.isEmpty()) return "Set your preferred size"
        val listed = d.variants.map { it.size.uppercase(Locale.ROOT) }.filter { it.isNotBlank() }
        if (listed.isEmpty()) return "Size data unavailable"
        return if (listed.any { it in sizes }) "Preferred size listed" else "Your size not listed in feed"
    }
    fun fitsPreferred(d: Deal): Boolean = fitStatus(d) != "Your size not listed in feed"

    fun setShipping(store: String, rate: Double?, freeAt: Double?) {
        val o = obj("shipping")
        if (rate == null || !rate.isFinite() || rate < 0) o.remove(store)
        else o.put(store, JSONObject().put("rate", rate).put("freeAt", freeAt ?: JSONObject.NULL))
        write("shipping", o)
    }
    fun shipping(store: String): Pair<Double?, Double?> {
        val o = obj("shipping").optJSONObject(store) ?: return Pair(null, null)
        return Pair(o.optDouble("rate").takeIf { it.isFinite() && it >= 0.0 },
            if (o.isNull("freeAt")) null else o.optDouble("freeAt").takeIf { it > 0.0 })
    }
    /** Shipping unknown until user supplies an estimate; same-store products share one charge. */
    fun quote(items: List<Deal>): ShippingEstimate {
        val byStore = items.groupBy { it.store }
        val missing = mutableListOf<String>()
        var delivery = 0.0
        byStore.forEach { (store, pieces) ->
            val (rate, freeAt) = shipping(store)
            val sub = pieces.sumOf { it.price }
            if (rate == null) missing += store
            else delivery += if (freeAt != null && sub >= freeAt) 0.0 else rate
        }
        return ShippingEstimate(items.sumOf { it.price }, if (missing.isEmpty()) delivery else null, missing)
    }

    fun score(d: Deal, favourites: List<Deal>): Double {
        val sameStyle = favourites.count { it.category == d.category && d.category != "Other" }
        val sameType = favourites.count { it.clothingType == d.clothingType }
        val sameStore = favourites.count { it.store == d.store }
        return sameStyle * 4.0 + sameType * 1.7 + sameStore * 0.7 +
            d.discount * 0.04 + if (recentlyDropped(d.id)) 4.0 else 0.0
    }

    fun outfitIdeas(deals: List<Deal>, budget: Double, style: String, fitOnly: Boolean): List<OutfitIdea> {
        val eligible = deals.filter { d ->
            d.price in 0.01..budget &&
                (style == "All" || d.category == style || d.category == "Other") &&
                (!fitOnly || fitsPreferred(d))
        }
        val tops = eligible.filter { it.clothingType == "Tops" }.sortedBy { it.price }.take(24)
        val bottoms = eligible.filter { it.clothingType in listOf("Skirts","Shorts","Pants") }.sortedBy { it.price }.take(24)
        val intimates = eligible.filter { it.clothingType == "Underwear" }.sortedBy { it.price }.take(18)
        val hosiery = eligible.filter { it.clothingType == "Stockings" }.sortedBy { it.price }.take(18)
        val ideas = mutableListOf<OutfitIdea>()
        for (top in tops) for (bottom in bottoms) {
            if (top.id == bottom.id) continue
            val initial = top.price + bottom.price
            if (initial > budget) continue
            val pieces = mutableListOf(top, bottom)
            val cohere = if (top.category == bottom.category) 2.0 else if (top.category == "Other" || bottom.category == "Other") 0.3 else -1.0
            val firstUndie = intimates.firstOrNull { it.id !in pieces.map(Deal::id) && pieces.sumOf(Deal::price) + it.price <= budget }
            if (firstUndie != null) pieces.add(firstUndie)
            val firstHose = hosiery.firstOrNull { it.id !in pieces.map(Deal::id) && pieces.sumOf(Deal::price) + it.price <= budget }
            if (firstHose != null) pieces.add(firstHose)
            val sum = pieces.sumOf(Deal::price)
            if (sum > budget) continue
            val rating = pieces.size * 4.0 + cohere * 3 + pieces.sumOf { it.discount * 0.03 } -
                sum / 25.0 + pieces.count { it.store == top.store } * 0.25
            ideas += OutfitIdea(pieces, rating, quote(pieces))
        }
        return ideas.sortedWith(compareByDescending<OutfitIdea> { it.score }.thenBy { it.subtotal })
            .distinctBy { it.pieces.map(Deal::id).joinToString("|") }
            .take(16)
    }

    fun recentlySeenCount(deals: List<Deal>): Int = deals.count { newlyAdded(it.id) }
    fun dropsCount(deals: List<Deal>): Int = deals.count { recentlyDropped(it.id) }
}
