-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.readinglog.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.readinglog.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
