# PDFBox
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.commons.logging.**
-keep class org.apache.commons.logging.** { *; }

# AndroidSVG
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**

# Commons Compress optional codecs
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-keep class org.apache.commons.compress.** { *; }
-keep class org.tukaani.xz.** { *; }

# Jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Keep native method names used by media codecs
-keepclasseswithmembers,allowshrinking class * {
    native <methods>;
}
