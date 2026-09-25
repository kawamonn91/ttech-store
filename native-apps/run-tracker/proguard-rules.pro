-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# kotlinx.serialization: @Serializable クラスのシリアライザを残す
-keepclassmembers class com.ttech.runtracker.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.runtracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ttech.runtracker.**$$serializer { *; }
