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
        DealWorker.schedule(applicationContext)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = berry, secondary = mauve, background = background, surface = Color.White),
                typography = Typography()
            ) {
                BloomScreen(
                    repo = repo,
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
private fun BloomScreen(repo: DealStore, openLink: (String) -> Unit) {
    var data by remember { mutableStateOf(repo.deals()) }
    var fav by remember { mutableStateOf(repo.favourites()) }
    var maxPrice by remember { mutableIntStateOf(repo.maxPrice()) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
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

    val filtered = remember(data, fav, maxPrice, query, category, onlyFav, sortDiscount) {
        data.asSequence()
            .filter { it.price <= maxPrice }
            .filter { !onlyFav || it.id in fav }
            .filter { category == "All" || it.category == category }
            .filter { query.isBlank() || "${it.name} ${it.store} ${it.tags}".contains(query.trim(), ignoreCase = true) }
            .let { if (sortDiscount) it.sortedWith(compareByDescending<Deal> { d -> d.discount }.thenBy { d -> d.price }) else it.sortedWith(compareBy { d -> d.price }) }
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
    }) { inset ->
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
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Coquette", "Y2K", "Goth / alt", "Lounge", "Other").forEach { chip ->
                        FilterChip(selected = category == chip, onClick = { category = chip }, label = { Text(chip) })
                    }
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
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(if (working) "Searching for tiny prices…" else "No matching finds yet. Try a higher price or refresh the stores.", color = mauve)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { deal ->
                        DealCard(
                            deal = deal, saved = deal.id in fav, history = repo.recentPrices(deal.id),
                            onFavourite = { repo.toggleFavourite(deal.id); fav = repo.favourites() },
                            onOpen = { openLink(deal.link) }
                        )
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
private fun DealCard(deal: Deal, saved: Boolean, history: List<Double>, onFavourite: () -> Unit, onOpen: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(19.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.background(Color.White).clickable(onClick = onOpen)) {
            Box(Modifier.fillMaxWidth().height(166.dp)) {
                if (deal.photo.isNotBlank()) {
                    AsyncImage(
                        model = deal.photo, contentDescription = deal.name,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(rose), contentAlignment = Alignment.Center) { Text("♡", fontSize = 42.sp, color = berry) }
                }
                IconButton(
                    onClick = onFavourite, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                        .background(Color.White.copy(alpha = 0.93f), RoundedCornerShape(24.dp))
                ) { Icon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Save favourite", tint = berry) }
                if (deal.discount >= 15) Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(7.dp), color = berry,
                    shape = RoundedCornerShape(8.dp)
                ) { Text("${deal.discount}% off", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 11.sp) }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(deal.store, style = MaterialTheme.typography.labelSmall, color = berry, maxLines = 1)
                Text(deal.name, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A$%.2f".format(deal.price), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = berry)
                }
                if (deal.comparePrice != null && deal.comparePrice > deal.price) {
                    Text("Compare-at A$%.2f".format(deal.comparePrice), color = Color.Gray, fontSize = 10.sp)
                }
                if (history.size > 1) {
                    Text("Recorded: " + history.takeLast(3).joinToString(" → ") { "A$%.0f".format(it) }, fontSize = 10.sp, color = mauve)
                }
                if (deal.availableSizes.isNotBlank()) Text("Listed sizes: ${deal.availableSizes}", color = mauve, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                Text("Checks publicly accessible Shopify feeds. Stores may block or disable their feed.", style = MaterialTheme.typography.bodySmall)
                current.forEach { shop ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = shop.enabled,
                            onCheckedChange = { repo.toggleShop(shop.baseUrl); current = repo.shops() }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(shop.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(shop.baseUrl.removePrefix("https://"), fontSize = 10.sp, color = Color.Gray)
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
