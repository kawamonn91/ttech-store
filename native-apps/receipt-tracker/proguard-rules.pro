-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.receipttracker.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.receipttracker.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
