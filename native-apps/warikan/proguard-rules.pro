-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.warikan.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.warikan.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
