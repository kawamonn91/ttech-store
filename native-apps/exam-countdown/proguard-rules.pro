-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.examcountdown.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.examcountdown.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
