-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.workoutlog.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.workoutlog.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
