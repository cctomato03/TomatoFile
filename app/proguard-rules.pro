# Keep model classes used through Gson reflection.
-keep class com.cctomato.tomatofile.data.model.** { *; }
-keep class com.cctomato.tomatofile.data.cache.** { *; }

# SMBJ / BouncyCastle
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }
-dontwarn javax.naming.**
-dontwarn java.awt.**