-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.familyquiz.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.familyquiz.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
