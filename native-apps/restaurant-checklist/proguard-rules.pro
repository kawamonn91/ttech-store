-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.restaurantchecklist.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.restaurantchecklist.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
