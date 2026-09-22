-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.childgrowth.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.childgrowth.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
