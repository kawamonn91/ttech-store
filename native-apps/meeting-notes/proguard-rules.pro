-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.meetingnotes.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.meetingnotes.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
