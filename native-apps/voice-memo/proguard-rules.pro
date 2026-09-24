-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.voicememo.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.voicememo.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
