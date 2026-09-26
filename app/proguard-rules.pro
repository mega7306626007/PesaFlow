# PesaFlow release keeps: receivers/services accessed by the system must survive shrinking.
-keep class com.pesaflow.app.data.parsers.SmsReceiver { *; }
-keep class com.pesaflow.app.data.notifications.PendingApproveReceiver { *; }
-keep class com.pesaflow.app.data.notifications.MealLogReceiver { *; }
-keep class com.pesaflow.app.data.notifications.MpesaNotificationListener { *; }
# Room / WorkManager / Compose ship their own rules; model classes stay for converters.
-keep class com.pesaflow.app.data.models.** { *; }
# kotlinx.serialization (backup v2): generated serializers are looked up by
# name at runtime — without these, release builds silently corrupt restores.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep @kotlinx.serialization.Serializable class com.pesaflow.app.data.models.** { *; }
-keep @kotlinx.serialization.Serializable class com.pesaflow.app.data.backup.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *; }
# Glance widgets inflate remotely — keep the provider surface.
-keep class com.pesaflow.app.ui.widget.** { *; }
