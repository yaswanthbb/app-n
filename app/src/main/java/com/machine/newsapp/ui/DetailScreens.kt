package com.machine.newsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.machine.newsapp.data.Deal
import com.machine.newsapp.data.Pick
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DeleteMenu(onDelete: () -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) { Icon(Icons.Outlined.MoreVert, "Item options") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { expanded = false; onDelete() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailFrame(title: String, onBack: () -> Unit, onDelete: (() -> Unit)?, linkLabel: String, url: String?, content: @Composable ColumnScope.() -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }, actions = { onDelete?.let { DeleteMenu(it) } })
        },
        bottomBar = {
            url?.let {
                Surface(tonalElevation = 3.dp) {
                    Button(onClick = { openArticle(context, it) }, modifier = Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding().heightIn(min = 52.dp)) { Text(linkLabel) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
    }
}

@Composable
fun DealDetailScreen(deal: Deal?, onBack: () -> Unit, onDelete: () -> Unit) {
    DetailFrame("Deal details", onBack, if (deal != null) onDelete else null, "Claim this offer ↗", deal?.url) {
        if (deal == null) {
            Text("This deal is no longer in your saved feed.")
            Text("Go back to see the latest offers.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            TagPill(deal.tag)
            Text(deal.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(deal.source, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(deal.description, style = MaterialTheme.typography.bodyLarge)
            deal.offerDetails?.takeIf { it.isNotBlank() }?.let {
                HorizontalDivider()
                Text("What you get", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            deal.claimSteps.orEmpty().filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { steps ->
                HorizontalDivider()
                Text("How to claim", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                steps.forEachIndexed { index, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${index + 1}.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
            deal.expires?.let { Text("Expires ${formattedDate(it)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }
        }
    }
}

@Composable
fun PickDetailScreen(pick: Pick?, onBack: () -> Unit, onDelete: () -> Unit) {
    DetailFrame("Machine's pick", onBack, if (pick != null) onDelete else null, "Open this pick ↗", pick?.url) {
        if (pick == null) {
            Text("This pick is no longer in your saved feed.")
            Text("Go back to see the latest picks.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(formattedDate(pick.date), color = MaterialTheme.colorScheme.secondary)
            Text(pick.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(pick.body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

fun formattedDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH))
}.getOrDefault(value)

@Composable
fun TagPill(tag: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(50)) {
        Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
    }
}
