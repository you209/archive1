package au.com.bargainbloom

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.util.Locale

private val orchid = Color(0xFF943D72)
private val plum = Color(0xFF68415E)
private val pale = Color(0xFFFFE7F1)
private fun aud(v: Double): String = "A$%.2f".format(Locale.US, v)

@Composable
fun BloomOutfits(extras: BloomExtras, deals: List<Deal>, open: (String) -> Unit) {
    var budget by remember { mutableFloatStateOf(50f) }
    var style by remember { mutableStateOf("All") }
    var fitOnly by remember { mutableStateOf(false) }
    val ideas = remember(deals, budget, style, fitOnly) { extras.outfitIdeas(deals, budget.toDouble(), style, fitOnly) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Outfit builder", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = orchid)
        Text("Combine actual scanned tops, bottoms, underwear and stockings. Your budget covers clothing only until shipping is configured.", color = plum, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Under A$" + budget.toInt(), fontWeight = FontWeight.Bold, modifier = Modifier.width(112.dp))
            Slider(value = budget, onValueChange = { budget = it }, valueRange = 20f..100f, steps = 15, modifier = Modifier.weight(1f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("All", "Coquette", "Y2K", "Goth / alt", "Lounge").forEach { c ->
                FilterChip(selected = style == c, onClick = { style = c }, label = { Text(c, fontSize = 11.sp) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = fitOnly, onCheckedChange = { fitOnly = it })
            Text("Prefer saved sizes where known", fontSize = 12.sp)
        }
        if (ideas.isEmpty()) Card {
            Text("No top-and-bottom combinations within the selected budget from scanned retailer feeds. Try a different style or visit store sale pages.", Modifier.padding(14.dp))
        }
        ideas.forEach { idea ->
            ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(idea.pieces.size.toString() + "-piece outfit", Modifier.weight(1f), color = orchid, fontWeight = FontWeight.Bold)
                        Text(aud(idea.subtotal) + " items", fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        idea.pieces.forEach { d ->
                            Column(Modifier.width(102.dp).clickable { open(d.link) }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                AsyncImage(model = d.photo, contentDescription = d.name,
                                    modifier = Modifier.size(100.dp).background(pale, RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
                                Text(d.name, maxLines = 2, minLines = 2, fontSize = 11.sp, lineHeight = 12.sp, overflow = TextOverflow.Ellipsis)
                                Text(aud(d.price), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = orchid)
                                Text(d.store, maxLines = 1, fontSize = 9.sp, color = plum)
                            }
                        }
                    }
                    val quote = idea.shipping
                    val delivered = quote.total
                    if (delivered != null) Text("Est. delivered " + aud(delivered) + " (entered shipping)", color = plum, fontSize = 11.sp)
                    else Text("Delivery not included. Unknown: " + quote.unknownStores.joinToString(), color = plum, fontSize = 11.sp)
                    Text("Tap each item to check stock, exact size and checkout price.", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun BloomExplore(extras: BloomExtras, repo: DealStore, deals: List<Deal>, open: (String) -> Unit) {
    var mode by remember { mutableStateOf("New") }
    var image by remember { mutableStateOf<Uri?>(null) }
    var matching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<VisualMatch>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            image = uri; matching = true; searched = false
            scope.launch {
                try { results = VisualMatcher.find(ctx, uri, deals) }
                catch (_: Exception) { results = emptyList() }
                finally { matching = false; searched = true }
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Discover", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = orchid)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("New", "Drops", "For you", "Photo").forEach { c ->
                FilterChip(selected = mode == c, onClick = { mode = c }, label = { Text(c) })
            }
        }
        if (mode == "Photo") {
            Text("Approximate colour and pattern similarity, not AI garment identification. Your photo stays on your phone.", fontSize = 12.sp, color = plum)
            Button(onClick = { picker.launch("image/*") }) { Text("Choose outfit photo") }
            image?.let { AsyncImage(model = it, contentDescription = "Selected outfit", modifier = Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit) }
            if (matching) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Comparing product photos…", fontSize = 12.sp) }
            else if (searched) {
                Text(if (results.isEmpty()) "No accessible photos to match. Scan feed retailers first." else results.size.toString() + " approximate matches")
                results.forEach { m -> BloomSmallDeal(m.deal, open, "Visual similarity " + (m.similarity*100).toInt() + "% (approximate)", extras) }
            }
        } else {
            val favourites = deals.filter { it.id in repo.favourites() }
            val chosen = when (mode) {
                "New" -> deals.filter { extras.newlyAdded(it.id) }.sortedBy { it.price }
                "Drops" -> deals.filter { extras.recentlyDropped(it.id) }.sortedBy { it.price }
                else -> deals.sortedWith(compareByDescending<Deal> { extras.score(it, favourites) }.thenBy { it.price }).take(60)
            }
            Text(when(mode) {
                "New" -> "First tracked by this app within seven days. Not necessarily new store arrivals."
                "Drops" -> "Products with recorded price reductions over the past seven days."
                else -> "Ranked from categories and stores in your favourites. Save items to personalise results."
            }, fontSize = 12.sp, color = plum)
            if (chosen.isEmpty()) Text("Nothing tracked in this category. Try scanning stores.", color = plum)
            chosen.take(55).forEach { d ->
                val note = when(mode) {
                    "Drops" -> "History: " + repo.recentPrices(d.id).takeLast(4).joinToString(" → ") { aud(it) }
                    "New" -> "First tracked this week"
                    else -> "Style match " + "%.1f".format(Locale.US, extras.score(d, favourites))
                }
                BloomSmallDeal(d, open, note, extras)
            }
        }
    }
}

@Composable
fun BloomSaved(extras: BloomExtras, repo: DealStore, deals: List<Deal>, open: (String) -> Unit) {
    var version by remember { mutableIntStateOf(0) }
    var collection by remember { mutableStateOf("All") }
    val current = remember(deals, version, collection) {
        deals.filter { it.id in repo.favourites() }
            .filter { collection == "All" || extras.collection(it.id).ifBlank { "Wishlist" } == collection }
            .sortedBy { it.price }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Saved wardrobe", style = MaterialTheme.typography.headlineSmall, color = orchid, fontWeight = FontWeight.Bold)
        Text("Create collections and set target-price notifications for saved items.", fontSize = 12.sp, color = plum)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (listOf("All") + BloomExtras.collections).forEach { c ->
                FilterChip(selected = c == collection, onClick = { collection = c }, label = { Text(c) })
            }
        }
        if (current.isEmpty()) Text("Save items with the heart icon in the Deals gallery.", color = plum)
        current.forEach { d ->
            var target by remember(d.id) { mutableStateOf(extras.targetPrice(d.id)?.toString() ?: "") }
            var folder by remember(d.id, version) { mutableStateOf(extras.collection(d.id).ifBlank { "Wishlist" }) }
            var menu by remember(d.id) { mutableStateOf(false) }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BloomSmallDeal(d, open, extras.fitStatus(d), extras)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Collection", fontSize = 12.sp)
                        Box {
                            TextButton(onClick = { menu = true }) { Text(folder) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                BloomExtras.collections.forEach { c ->
                                    DropdownMenuItem(text = { Text(c) }, onClick = {
                                        folder = c; extras.saveCollection(d.id, c); menu = false; version++
                                    })
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = target, onValueChange = { target = it },
                            modifier = Modifier.weight(1f), label = { Text("Alert below A$") }, singleLine = true)
                        Button(onClick = { extras.setTargetPrice(d.id, target.toDoubleOrNull()); version++ }) { Text("Save") }
                        TextButton(onClick = { repo.toggleFavourite(d.id); version++ }) { Text("×") }
                    }
                }
            }
        }
    }
}

@Composable
fun BloomStores(extras: BloomExtras, repo: DealStore, open: (String) -> Unit, manage: () -> Unit) {
    var edit by remember { mutableStateOf<Shop?>(null) }
    var fit by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Stores + fit", style = MaterialTheme.typography.headlineSmall, color = orchid, fontWeight = FontWeight.Bold)
        Text("Feed attempt means the app tries a public product feed; it may be blocked. Browse-only stores open official live pages.", color = plum, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { fit = true }) { Text("My sizes") }
            OutlinedButton(onClick = manage) { Text("Manage stores") }
        }
        Text("Tasmania shipping is unknown unless you enter an estimate from the retailer. These are not live checkout quotes.", fontSize = 11.sp, color = plum)
        repo.shops().filter { it.enabled }.forEach { shop ->
            val quote = extras.shipping(shop.name)
            ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(shop.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(if (shop.isFeed) "Feed attempt" else "Browse only", fontSize = 10.sp, color = plum)
                    }
                    Text(if (quote.first == null) "Shipping: unknown" else
                        "Est. shipping " + aud(quote.first!!) + (quote.second?.let { " · free above " + aud(it) } ?: ""),
                        fontSize = 11.sp, color = plum)
                    Row {
                        TextButton(onClick = { open(shop.browseUrl) }) { Text("Sale page") }
                        TextButton(onClick = { edit = shop }) { Text("Shipping rate") }
                    }
                }
            }
        }
    }
    edit?.let { shop ->
        val initial = extras.shipping(shop.name)
        var rate by remember(shop.name) { mutableStateOf(initial.first?.toString() ?: "") }
        var threshold by remember(shop.name) { mutableStateOf(initial.second?.toString() ?: "") }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text(shop.name + " shipping") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Enter a retailer-quoted Tasmania shipping cost; leave blank if unknown.", fontSize = 12.sp)
                OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Shipping A$") })
                OutlinedTextField(value = threshold, onValueChange = { threshold = it }, label = { Text("Free above A$ (optional)") })
            } },
            confirmButton = { TextButton(onClick = {
                extras.setShipping(shop.name, rate.toDoubleOrNull(), threshold.toDoubleOrNull()); revision++; edit = null
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } }
        )
    }
    if (fit) {
        val p = extras.profile()
        var height by remember { mutableStateOf(p.heightCm.toString()) }
        var waist by remember { mutableStateOf(p.waistInches.toString()) }
        var tops by remember { mutableStateOf(p.topSizes) }
        var bottoms by remember { mutableStateOf(p.bottomSizes) }
        var undies by remember { mutableStateOf(p.underwearSizes) }
        var bras by remember { mutableStateOf(p.braSizes) }
        AlertDialog(onDismissRequest = { fit = false }, title = { Text("My preferred sizes") },
            text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Sizes vary by brand. These are preferences only, not fit guarantees. Bra sizing needs measured underbust and bust.", fontSize = 12.sp, color = plum)
                OutlinedTextField(height, { height = it }, label = { Text("Height cm") })
                OutlinedTextField(waist, { waist = it }, label = { Text("Waist inches") })
                OutlinedTextField(tops, { tops = it }, label = { Text("Top sizes e.g. M,L") })
                OutlinedTextField(bottoms, { bottoms = it }, label = { Text("Bottom sizes e.g. 12,14") })
                OutlinedTextField(undies, { undies = it }, label = { Text("Underwear e.g. M,L") })
                OutlinedTextField(bras, { bras = it }, label = { Text("Bra size e.g. 12B,14C") })
            } },
            confirmButton = { TextButton(onClick = {
                extras.saveProfile(FitProfile(height.toIntOrNull() ?: p.heightCm, waist.toIntOrNull() ?: p.waistInches,
                    tops, bottoms, undies, bras)); fit = false
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { fit = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BloomSmallDeal(d: Deal, open: (String) -> Unit, note: String, extras: BloomExtras) {
    Row(Modifier.fillMaxWidth().clickable { open(d.link) }.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        AsyncImage(model = d.photo, contentDescription = d.name,
            modifier = Modifier.size(72.dp).background(pale, RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(d.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(d.store + " · " + d.clothingType, fontSize = 10.sp, color = plum)
            Text(aud(d.price), fontWeight = FontWeight.Bold, color = orchid)
            if (note.isNotBlank()) Text(note, fontSize = 10.sp, color = plum, maxLines = 2)
            extras.quote(listOf(d)).total?.let { Text("Est. delivered " + aud(it), fontSize = 10.sp, color = plum) }
        }
    }
    HorizontalDivider()
}
