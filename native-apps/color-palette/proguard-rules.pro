-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.colorpalette.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.colorpalette.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
