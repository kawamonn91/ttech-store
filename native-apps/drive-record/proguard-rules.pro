-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# kotlinx.serialization: @Serializable クラスのシリアライザを残す
-keepclassmembers class com.ttech.driverecord.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.driverecord.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ttech.driverecord.**$$serializer { *; }
