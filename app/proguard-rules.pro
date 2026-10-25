# OpenCV
-keep class org.opencv.** { *; }

# Tesseract4Android (JNI)
-keep class com.googlecode.tesseract.android.** { *; }

# PDFBox-Android
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.pdfbox.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.openscan.data.** {
    *** Companion;
}
-keepclasseswithmembers class app.openscan.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
