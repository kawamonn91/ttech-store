-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.subscriptionmanager.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.subscriptionmanager.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
