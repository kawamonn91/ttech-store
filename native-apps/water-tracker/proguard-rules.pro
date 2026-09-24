-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.watertracker.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.watertracker.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
