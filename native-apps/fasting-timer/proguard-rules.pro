-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.fastingtimer.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.fastingtimer.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
