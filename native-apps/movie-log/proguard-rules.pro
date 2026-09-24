-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.movielog.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.movielog.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
