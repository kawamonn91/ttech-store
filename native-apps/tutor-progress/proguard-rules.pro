-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.tutorprogress.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.tutorprogress.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
