-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ttech.flashcards.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.flashcards.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
