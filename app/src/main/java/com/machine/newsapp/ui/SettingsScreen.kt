package com.machine.newsapp.ui

import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: TokenSettingsState, onSave: (String) -> Unit, onClear: () -> Unit, onBack: () -> Unit) {
    // Never load the stored value into this field or remember it across process
    // recreation. Only this transient, masked entry is passed to secure storage.
    var enteredToken by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val alreadySecure = activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!alreadySecure) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Publishing access", style = MaterialTheme.typography.titleLarge)
            Text("Save your publishing token to delete deals and picks. Reading feeds doesn't require it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.hasToken) Text("A token is saved on this device.", color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = enteredToken, onValueChange = { enteredToken = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text(if (state.hasToken) "Replace saved token" else "Publishing token") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { val value = enteredToken.trim(); enteredToken = ""; focus.clearFocus(); onSave(value) }, enabled = enteredToken.isNotBlank() && !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.busy) "Saving…" else "Save token")
            }
            if (state.hasToken) OutlinedButton(onClick = { enteredToken = ""; onClear() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Remove saved token") }
            Text("Stored encrypted on this device. Used only for confirmed deletions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
