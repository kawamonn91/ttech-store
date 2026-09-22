-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.cycletracker.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.cycletracker.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
