-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.dailyfortune.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.dailyfortune.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
