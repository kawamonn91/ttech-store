-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.tastingnotes.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.tastingnotes.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
