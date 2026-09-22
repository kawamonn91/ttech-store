-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.expensetracker.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.expensetracker.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
