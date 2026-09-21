-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.businesscardcontacts.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.businesscardcontacts.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
