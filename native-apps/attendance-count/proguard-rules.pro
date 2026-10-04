-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.attendancecount.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.attendancecount.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
