# Room: entidades y DAO
-keep class com.ucd.player.data.** { *; }
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-keep class com.ucd.player.player.** { *; }
-keep class androidx.media3.** { *; }

# yt-dlp (youtubedl-android): usa reflexión/JSON y lanza binarios nativos
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class com.ucd.player.download.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.**
