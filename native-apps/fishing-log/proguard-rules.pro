-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.fishinglog.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.fishinglog.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
