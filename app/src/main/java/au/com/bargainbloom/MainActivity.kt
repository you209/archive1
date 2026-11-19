package au.com.bargainbloom

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private val berry = Color(0xFF943D72)
private val background = Color(0xFFFFF9FD)
private val mauve = Color(0xFF6B435E)
private val rose = Color(0xFFFFE8F3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = DealStore(applicationContext)
        val extras = BloomExtras(applicationContext)
        DealWorker.schedule(applicationContext)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = berry, secondary = mauve, background = background, surface = Color.White),
                typography = Typography()
            ) {
                BloomScreen(
                    repo = repo,
                    extras = extras,
                    openLink = { link ->
                        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) } catch (_: Exception) { }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BloomScreen(repo: DealStore, extras: BloomExtras, openLink: (String) -> Unit) {
    var data by remember { mutableStateOf(repo.deals()) }
    var fav by remember { mutableStateOf(repo.favourites()) }
    var maxPrice by remember { mutableIntStateOf(repo.maxPrice()) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var typeChoice by remember { mutableStateOf("Any") }
    var colourChoice by remember { mutableStateOf("Any") }
    var sizeChoice by remember { mutableStateOf("Any") }
    var storeChoice by remember { mutableStateOf("Any") }
    var browseStores by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf("Deals") }
    var onlyFav by remember { mutableStateOf(false) }
    var sortDiscount by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(repo.lastErrors()) }
    var report by remember { mutableStateOf("") }
    var showShops by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val scan: () -> Unit = {
        if (!working) {
            working = true
            report = ""
            scope.launch {
                try {
                    val result = repo.scan { progress = it }
                    data = repo.deals()
                    errors = result.errors
                    report = "Checked ${result.scanned} listings · ${result.deals} stored · ${result.drops.size} price drops"
                } catch (e: Exception) {
                    report = "Scan failed: ${e.message ?: "unknown error"}"
                } finally {
                    working = false
                    progress = ""
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (data.isEmpty()) scan()
    }

    val filtered = remember(data, fav, maxPrice, query, category, typeChoice, colourChoice, sizeChoice, storeChoice, onlyFav, sortDiscount) {
        data.asSequence()
            .filter { !onlyFav || it.id in fav }
            .filter { category == "All" || it.category == category }
            .filter { typeChoice == "Any" || it.clothingType == typeChoice }
            .filter { storeChoice == "Any" || it.store == storeChoice }
            .filter { query.isBlank() || "${it.name} ${it.store} ${it.tags}".contains(query.trim(), ignoreCase = true) }
            .mapNotNull { it.matching(sizeChoice, colourChoice) }
            .filter { it.price <= maxPrice }
            .let { matches ->
                if (sortDiscount) matches.sortedWith(compareByDescending<DealMatch> { it.deal.discount }.thenBy { it.price })
                else matches.sortedBy { it.price }
            }
            .toList()
    }

    Scaffold(containerColor = background, topBar = {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
            title = {
                Column {
                    Text("bargain bloom", fontSize = 25.sp, fontWeight = FontWeight.Black, color = berry)
                    Text("Cute finds. Tiny prices.", fontSize = 12.sp, color = mauve)
                }
            },
            actions = {
                IconButton(onClick = scan, enabled = !working) { Icon(Icons.Default.Refresh, contentDescription = "Check prices") }
                IconButton(onClick = { showShops = true }) { Icon(Icons.Default.Settings, contentDescription = "Manage stores") }
            }
        )
    }, bottomBar = {
        NavigationBar(containerColor = Color.White) {
            listOf("Deals", "Outfits", "Discover", "Saved", "Stores").forEach { page ->
                NavigationBarItem(
                    selected = section == page,
                    onClick = { section = page },
                    icon = { Icon(when (page) {
                        "Outfits" -> Icons.Default.Checkroom
                        "Discover" -> Icons.Default.AutoAwesome
                        "Saved" -> Icons.Default.Favorite
                        "Stores" -> Icons.Default.Storefront
                        else -> Icons.Default.Search
                    }, contentDescription = page) },
                    label = { Text(page, fontSize = 10.sp) },
                    alwaysShowLabel = true
                )
            }
        }
    }) { inset ->
        if (section != "Deals") {
            Box(Modifier.padding(inset).fillMaxSize()) {
                when (section) {
                    "Outfits" -> BloomOutfits(extras, data, openLink)
                    "Discover" -> BloomExplore(extras, repo, data, openLink)
                    "Saved" -> BloomSaved(extras, repo, data, openLink)
                    "Stores" -> BloomStores(extras, repo, openLink) { showShops = true }
                }
            }
        } else {
        Column(Modifier.padding(inset).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {{ IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear search") } }} else null,
                    placeholder = { Text("Search lace, mini skirts, goth…") }
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MAX A$$maxPrice", fontWeight = FontWeight.Bold, color = mauve, fontSize = 13.sp)
                    Slider(
                        value = maxPrice.toFloat(), onValueChange = { maxPrice = it.roundToInt() },
                        onValueChangeFinished = { repo.setMaxPrice(maxPrice) },
                        valueRange = 5f..100f, steps = 18, modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !browseStores, onClick = { browseStores = false }, label = { Text("Photo deals") })
                    FilterChip(selected = browseStores, onClick = { browseStores = true }, label = { Text("All retailers") })
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Coquette", "Y2K", "Goth / alt", "Lounge", "Other").forEach { chip ->
                        FilterChip(selected = category == chip, onClick = { category = chip }, label = { Text(chip) })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceFilter("Clothing", typeChoice,
                        listOf("Any", "Tops", "Dresses", "Skirts", "Shorts", "Pants", "Underwear", "Stockings", "Sleepwear", "Outerwear", "Shoes", "Other")
                    ) { typeChoice = it }
                    ChoiceFilter("Colour", colourChoice,
                        listOf("Any", "Black", "White", "Pink", "Red", "Purple", "Blue", "Green", "Brown", "Cream", "Beige", "Grey", "Yellow", "Orange", "Navy", "Multi")
                    ) { colourChoice = it }
                    ChoiceFilter("Size", sizeChoice,
                        listOf("Any", "4", "6", "8", "10", "12", "14", "16", "18", "20", "22", "24", "26", "XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL")
                    ) { sizeChoice = it }
                    ChoiceFilter("Store", storeChoice,
                        listOf("Any") + repo.shops().filter { it.enabled && it.isFeed }.map { it.name }
                    ) { storeChoice = it }
                }
                if (sizeChoice != "Any" || colourChoice != "Any" || typeChoice != "Any" || storeChoice != "Any") {
                    TextButton(onClick = {
                        sizeChoice = "Any"; colourChoice = "Any"; typeChoice = "Any"; storeChoice = "Any"
                    }, contentPadding = PaddingValues(0.dp)) { Text("Clear all filters") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = onlyFav, onClick = { onlyFav = !onlyFav }, label = { Text("♥ Saved") })
                    FilterChip(selected = sortDiscount, onClick = { sortDiscount = !sortDiscount }, label = { Text("Biggest discount") })
                }
                if (working) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(progress.ifBlank { "Checking stores…" }, style = MaterialTheme.typography.bodySmall, color = mauve)
                } else {
                    val last = repo.lastScan()
                    Text(
                        when {
                            browseStores -> "Open live sale pages at Australian retailers"
                            report.isNotBlank() -> report
                            last != 0L -> "${filtered.size} finds · Last check ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(last))}"
                            else -> "Press refresh to search supported stores"
                        },
                        style = MaterialTheme.typography.bodySmall, color = mauve
                    )
                }
                if (errors.isNotEmpty()) {
                    TextButton(onClick = { showErrors = true }, contentPadding = PaddingValues(0.dp)) {
                        Text("${errors.size} store warning(s) — see details")
                    }
                }
            }
            if (browseStores) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Browse live clothing photos and prices at each store. Browse-only shops cannot populate the automatic photo-deals grid.",
                        style = MaterialTheme.typography.bodySmall, color = mauve
                    )
                    repo.shops().filter { it.enabled }.forEach { shop ->
                        RetailerCard(
                            shop = shop,
                            onBrowse = { openLink(shop.browseUrl) },
                            onSearch = { openLink(searchThisStore(shop, query, typeChoice, colourChoice, sizeChoice, maxPrice)) }
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(if (working) "Searching for tiny prices…" else "No matching finds yet. Try a higher price or refresh the stores.", color = mauve)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.deal.id }) { match ->
                        val deal = match.deal
                        DealCard(
                            deal = deal, extras = extras, displayedPrice = match.price, displayedPhoto = match.photo,
                            selectedSize = sizeChoice, selectedColour = colourChoice,
                            saved = deal.id in fav, history = repo.recentPrices(deal.id),
                            onFavourite = { repo.toggleFavourite(deal.id); fav = repo.favourites() },
                            onOpen = { openLink(deal.link) }
                        )
                    }
                }
            }
        }
        }
    }

    if (showShops) {
        ShopDialog(repo = repo, onClose = { showShops = false }, onRefresh = scan)
    }
    if (showErrors) AlertDialog(
        onDismissRequest = { showErrors = false }, title = { Text("Store scan warnings") },
        text = { Text(errors.joinToString("\n\n") + "\n\nStores may block automated feeds. Existing cached products can be stale; always check the retailer before buying.") },
        confirmButton = { TextButton(onClick = { showErrors = false }) { Text("OK") } }
    )
}

@Composable
private fun DealCard(
    deal: Deal, extras: BloomExtras, displayedPrice: Double, displayedPhoto: String, selectedSize: String, selectedColour: String,
    saved: Boolean, history: List<Double>, onFavourite: () -> Unit, onOpen: () -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(19.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.background(Color.White).clickable(onClick = onOpen)) {
            Box(Modifier.fillMaxWidth().height(166.dp)) {
                if (displayedPhoto.isNotBlank()) {
                    AsyncImage(
                        model = displayedPhoto, contentDescription = deal.name,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(rose), contentAlignment = Alignment.Center) { Text("♡", fontSize = 42.sp, color = berry) }
                }
                IconButton(
                    onClick = onFavourite, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                        .background(Color.White.copy(alpha = 0.93f), RoundedCornerShape(24.dp))
                ) { Icon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Save favourite", tint = berry) }
                if (deal.discount >= 15 && displayedPrice == deal.price) Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(7.dp), color = berry,
                    shape = RoundedCornerShape(8.dp)
                ) { Text("${deal.discount}% off", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 11.sp) }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(deal.store, style = MaterialTheme.typography.labelSmall, color = berry, maxLines = 1)
                Text(deal.name, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A$%.2f".format(displayedPrice), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = berry)
                }
                if (displayedPrice == deal.price && deal.comparePrice != null && deal.comparePrice > deal.price) {
                    Text("Compare-at A$%.2f".format(deal.comparePrice), color = Color.Gray, fontSize = 10.sp)
                }
                val priceRange = history.takeLast(20)
                if (priceRange.size > 1 && displayedPrice <= (priceRange.minOrNull() ?: 0.0)) {
                    Text("Lowest tracked price", fontSize = 10.sp, color = berry, fontWeight = FontWeight.Bold)
                }
                if (extras.newlyAdded(deal.id)) Text("New to this app", fontSize = 10.sp, color = mauve)
                if (extras.recentlyDropped(deal.id)) Text("Recorded price drop", fontSize = 10.sp, color = berry)
                val shipping = extras.quote(listOf(deal))
                if (shipping.total != null) {
                    Text("Est. delivered A$%.2f".format(shipping.total), fontSize = 10.sp, color = mauve)
                } else {
                    Text("Shipping not included", fontSize = 10.sp, color = Color.Gray)
                }
                if (history.size > 1) {
                    Text("Recorded: " + history.takeLast(3).joinToString(" → ") { "A$%.0f".format(it) }, fontSize = 10.sp, color = mauve)
                }
                if (selectedSize != "Any") Text("In stock size: $selectedSize", color = mauve, fontSize = 10.sp)
                else if (deal.availableSizes.isNotBlank()) Text("Listed sizes: ${deal.availableSizes}", color = mauve, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (selectedColour != "Any") Text("Colour: $selectedColour", color = mauve, fontSize = 10.sp)
                if (deal.variants.isNotEmpty() && extras.fitStatus(deal) == "Preferred size listed") {
                    Text("Preferred size listed (verify brand fit)", fontSize = 10.sp, color = berry)
                }
                val daysOld = ((System.currentTimeMillis() - deal.lastSeen) / 86_400_000L).toInt()
                if (daysOld > 0) Text("Cached · last seen $daysOld day(s) ago", fontSize = 10.sp, color = Color(0xFFA05A1A))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("View at store", color = berry, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = berry, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ShopDialog(repo: DealStore, onClose: () -> Unit, onRefresh: () -> Unit) {
    var current by remember { mutableStateOf(repo.shops()) }
    var newName by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Stores to check") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Some stores support direct price scanning. Browse-only retailers have live sale links, but are not automatically scanned.", style = MaterialTheme.typography.bodySmall)
                current.forEach { shop ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = shop.enabled,
                            onCheckedChange = { repo.toggleShop(shop.baseUrl); current = repo.shops() }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(shop.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text((if (shop.isFeed) "Auto-feed · " else "Browse-only · ") + shop.baseUrl.removePrefix("https://"), fontSize = 10.sp, color = Color.Gray)
                        }
                        IconButton(onClick = { repo.removeShop(shop.baseUrl); current = repo.shops() }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove ${shop.name}")
                        }
                    }
                }
                HorizontalDivider()
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Store name") }, singleLine = true)
                OutlinedTextField(value = newUrl, onValueChange = { newUrl = it }, label = { Text("https://store.com.au") }, singleLine = true)
                TextButton(onClick = {
                    if (repo.addShop(newName, newUrl)) {
                        current = repo.shops(); newName = ""; newUrl = ""; message = "Store added. Press Scan when ready."
                    } else message = "Enter a new HTTPS store address."
                }) { Icon(Icons.Default.Add, contentDescription = null); Text("Add store") }
                if (message.isNotBlank()) Text(message, color = berry, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onClose(); onRefresh() }) { Text("Save & scan") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}

@Composable
private fun ChoiceFilter(label: String, selected: String, values: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("$label: $selected", maxLines = 1, fontSize = 12.sp) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value ->
                DropdownMenuItem(text = { Text(value) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun RetailerCard(shop: Shop, onBrowse: () -> Unit, onSearch: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(shop.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Surface(color = if (shop.isFeed) rose else Color(0xFFF0F0F0), shape = RoundedCornerShape(8.dp)) {
                    Text(if (shop.isFeed) "Auto-feed" else "Browse only",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp, color = mauve)
                }
            }
            Text(
                if (shop.isFeed) "Photos and prices where its public feed is accessible"
                else "View the retailer's live photos, prices, colours and available sizes",
                style = MaterialTheme.typography.bodySmall, color = mauve
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onBrowse) { Text("Open sale", fontSize = 12.sp) }
                OutlinedButton(onClick = onSearch) { Text("Search filters", fontSize = 12.sp) }
            }
        }
    }
}

private fun searchThisStore(shop: Shop, query: String, type: String, colour: String, size: String, maxPrice: Int): String {
    val domain = Uri.parse(shop.baseUrl).host.orEmpty()
    val terms = listOfNotNull("site:$domain", "women", "sale",
        query.takeIf { it.isNotBlank() },
        type.takeIf { it != "Any" },
        colour.takeIf { it != "Any" },
        size.takeIf { it != "Any" }?.let { "size $it" },
        "under AUD $maxPrice"
    )
    return "https://www.google.com/search?q=" + java.net.URLEncoder.encode(terms.joinToString(" "), "UTF-8")
}
