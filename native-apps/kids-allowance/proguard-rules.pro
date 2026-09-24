-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.kidsallowance.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.kidsallowance.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
