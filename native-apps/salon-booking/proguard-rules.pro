-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.salonbooking.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.salonbooking.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
