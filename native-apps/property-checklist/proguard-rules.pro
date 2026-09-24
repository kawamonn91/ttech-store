-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.propertychecklist.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.propertychecklist.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
