-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.oneononelog.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.oneononelog.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
