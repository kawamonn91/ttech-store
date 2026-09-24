-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.roomcheckin.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.roomcheckin.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn com.google.zxing.**
