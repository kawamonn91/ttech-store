-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.weightlog.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.weightlog.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
