# Keep OkHttp (networking)
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep model classes used with org.json
-keepclassmembers class com.buildai.app.data.model.** { *; }
