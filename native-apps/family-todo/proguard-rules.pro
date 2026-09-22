-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.familytodo.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.familytodo.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
