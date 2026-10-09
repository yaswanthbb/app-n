package com.machine.newsapp.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.machine.newsapp.data.isWebUrl

fun openArticle(context: Context, url: String) {
    if (!isWebUrl(url)) {
        Toast.makeText(context, "This link isn't valid.", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = url.toUri()
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(context, "Install a browser to open this link.", Toast.LENGTH_LONG).show() }
    }
}
