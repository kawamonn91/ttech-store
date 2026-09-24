-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.stretchreminder.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.stretchreminder.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
