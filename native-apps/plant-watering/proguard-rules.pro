-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.plantwatering.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.plantwatering.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
