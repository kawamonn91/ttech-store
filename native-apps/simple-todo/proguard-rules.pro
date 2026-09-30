-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.simpletodo.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.simpletodo.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
