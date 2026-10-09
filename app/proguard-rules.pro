# Room, Retrofit and kotlinx.serialization ship their own consumer R8 rules.
# Keep the classes WorkManager constructs by name from its persisted queue.
-keep class com.machine.newsapp.notifications.DailyDigestWorker { public <init>(...); }
-keep class com.machine.newsapp.notifications.TopicSubscriptionWorker { public <init>(...); }
