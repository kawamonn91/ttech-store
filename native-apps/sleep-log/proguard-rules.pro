-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.sleeplog.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.sleeplog.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
