-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.voicememo.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.voicememo.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
