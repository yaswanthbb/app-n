package com.machine.newsapp.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class NewsMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val tab = message.data[Notifications.EXTRA_TAB] ?: message.from?.substringAfter("/topics/") ?: "news"
        val title = message.notification?.title ?: message.data["title"] ?: "A new daily find"
        val body = message.notification?.body ?: if (tab == "picks") "Machine's latest pick is ready." else "A new free offer is ready to claim."
        val id = message.data["item_id"]?.toIntOrNull() ?: (message.messageId ?: "$tab:$title").hashCode()
        Notifications.show(this, title, body, tab, id)
    }
    override fun onNewToken(token: String) {
        // Topics are the delivery address; no personal device token leaves the app.
        TopicSubscriptionWorker.schedule(this)
    }
}
