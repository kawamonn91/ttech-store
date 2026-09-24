-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.invoicemaker.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.invoicemaker.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
