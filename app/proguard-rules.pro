# Anotasi yang hanya dipakai saat compile (Guava / Media3) — aman diabaikan
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn kotlin.annotations.jvm.**

# Nama class tetap utuh supaya pesan error mudah dibaca
-dontobfuscate

# ---------- Download YouTube (NewPipeExtractor) ----------
# Extractor, mesin JavaScript Rhino, dan protobuf memakai refleksi — jangan dipangkas
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-keep class javax.script.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-dontwarn com.google.protobuf.**
-dontwarn com.google.re2j.**
-dontwarn org.jspecify.**

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
