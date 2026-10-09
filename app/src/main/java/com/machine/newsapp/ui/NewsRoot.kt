package com.machine.newsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.machine.newsapp.data.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class Tab(val kind: FeedKind, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab(FeedKind.NEWS, "NEWS", Icons.AutoMirrored.Outlined.Article),
    Tab(FeedKind.DEALS, "DEALS", Icons.Outlined.LocalOffer),
    Tab(FeedKind.PICKS, "MACHINE'S PICKS", Icons.Outlined.AutoAwesome),
)

@Composable
fun NewsRoot(vm: FeedViewModel, target: String?, onTargetConsumed: () -> Unit, notificationsEnabled: Boolean, enableNotifications: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: FeedKind.NEWS.route
    val selected by vm.filter.collectAsStateWithLifecycle()
    val news by vm.news.collectAsStateWithLifecycle()
    val deals by vm.deals.collectAsStateWithLifecycle()
    val picks by vm.picks.collectAsStateWithLifecycle()
    val statuses by vm.statuses.collectAsStateWithLifecycle()
    var hidePermission by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = Instant.now() } }
    fun navigate(to: String) {
        nav.navigate(to) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    LaunchedEffect(target) {
        if (target != null) {
            navigate(target)
            FeedKind.entries.firstOrNull { it.route == target }?.let { vm.refresh(it) }
            onTargetConsumed()
        }
    }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.kind.route,
                        onClick = { navigate(tab.kind.route); vm.refresh(tab.kind, force = false) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NEWS APP", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                    Text("A little signal. Every day.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)).uppercase(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!notificationsEnabled && !hidePermission) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.NotificationsNone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text("Your daily heads-up", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                        }
                        Text("Get new deals, Machine's pick, and a morning digest.", fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        Row {
                            TextButton(onClick = enableNotifications) { Text("Enable alerts") }
                            TextButton(onClick = { hidePermission = true }) { Text("Later") }
                        }
                    }
                }
            }
            NavHost(navController = nav, startDestination = "news", modifier = Modifier.weight(1f)) {
                composable("news") {
                    val status = statuses[vm.key(FeedKind.NEWS, selected)] ?: RefreshStatus()
                    FeedPage("The daily briefing", "TECH, AI & THE WORLD AROUND YOU", news.fetchedAt, status,
                        { vm.refresh(FeedKind.NEWS) }, news.items.isEmpty(), "No headlines yet", "Fresh stories will appear here when your news feed is connected.",
                        filters = {
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NewsFilter.entries.forEach { filter -> FilterChip(selected = selected == filter, onClick = { vm.selectFilter(filter) }, label = { Text(filter.label) }) }
                            }
                        }, listKey = selected.name) {
                        items(news.items, key = { it.url }) { ArticleCard(it, now) }
                    }
                }
                composable("deals") {
                    FeedPage("Good things. Free.", "AI · DEV TOOLS · SOFTWARE", deals.fetchedAt, statuses["deals"] ?: RefreshStatus(),
                        { vm.refresh(FeedKind.DEALS) }, deals.items.isEmpty(), "No free offers right now", "New free tools and offers will land here. Pull down to check again.") {
                        items(deals.items, key = { it.url }) { DealCard(it) }
                    }
                }
                composable("picks") {
                    FeedPage("Machine's picks", "ONE THING WORTH YOUR TIME", picks.fetchedAt, statuses["picks"] ?: RefreshStatus(),
                        { vm.refresh(FeedKind.PICKS) }, picks.items.isEmpty(), "Today's pick is on its way", "One launch, tool, idea, or resource. Hand-picked every day.") {
                        items(picks.items, key = { it.date }) { PickCard(it) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedPage(
    title: String, subtitle: String, fetchedAt: Long?, status: RefreshStatus,
    refresh: () -> Unit, empty: Boolean, emptyTitle: String, emptyBody: String,
    filters: @Composable () -> Unit = {}, listKey: String = "feed", cards: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 12.dp)) {
            Text(subtitle, fontSize = 10.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fetchedAt?.let { "Saved ${timeAgo(Instant.ofEpochMilli(it).toString())}" } ?: "Your daily dose of signal", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                IconButton(onClick = refresh, enabled = !status.refreshing, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Refresh, "Refresh feed", modifier = Modifier.size(18.dp)) }
            }
        }
        filters()
        PullToRefreshBox(isRefreshing = status.refreshing, onRefresh = refresh, modifier = Modifier.weight(1f)) {
            key(listKey) {
                LazyColumn(state = rememberLazyListState(), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    status.error?.let { error ->
                        item("error") {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = refresh, enabled = !status.refreshing) { Text("Retry") }
                                }
                            }
                        }
                    }
                    if (empty) item("empty") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (status.refreshing) {
                                CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 2.dp)
                                Text("Finding your next good read…", modifier = Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Text(emptyTitle, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
                                Text(emptyBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    } else cards()
                }
            }
        }
    }
}

@Composable
private fun ArticleCard(article: Article, now: Instant) {
    val context = LocalContext.current
    Card(onClick = { openArticle(context, article.url) }, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("${article.source.name}  ·  ${timeAgo(article.publishedAt, now)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(article.title, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.Article, null, tint = MaterialTheme.colorScheme.outline)
                    if (article.image?.let { isWebUrl(it) } == true) AsyncImage(model = article.image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Text(article.description?.takeIf { it.isNotBlank() } ?: "Read the full story", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun DealCard(deal: Deal) {
    val context = LocalContext.current
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
                    Text(deal.tag, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("FREE", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            }
            Text(deal.title, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            Text(deal.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(deal.source, style = MaterialTheme.typography.labelMedium)
                    deal.expires?.let { Text("Until $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Button(onClick = { openArticle(context, deal.url) }, shape = RoundedCornerShape(12.dp)) { Text("Claim") }
            }
        }
    }
}

@Composable
private fun PickCard(pick: Pick) {
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(pick.date).format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)) }.getOrDefault(pick.date)
    Card(onClick = { openArticle(context, pick.url) }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(date.uppercase(), fontSize = 10.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
            Text(pick.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            Text(pick.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            Text("Take a look ↗", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 20.dp))
        }
    }
}
