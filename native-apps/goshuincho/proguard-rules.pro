-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.goshuincho.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.goshuincho.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
