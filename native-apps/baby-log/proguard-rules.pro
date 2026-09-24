-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.babylog.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.babylog.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
