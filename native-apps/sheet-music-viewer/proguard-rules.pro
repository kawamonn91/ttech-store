-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.sheetmusicviewer.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.sheetmusicviewer.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
