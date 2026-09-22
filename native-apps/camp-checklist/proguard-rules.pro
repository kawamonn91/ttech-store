-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.campchecklist.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.campchecklist.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
