-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.plantwatering.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.plantwatering.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
