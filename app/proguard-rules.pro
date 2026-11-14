-keep class com.masarify.app.data.** { *; }
-keepclassmembers class com.masarify.app.data.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**
# Widget (Glance) 
-keep class com.masarify.app.widget.** { *; }
# JSON
-keep class org.json.** { *; }
