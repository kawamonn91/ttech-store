-keepattributes *Annotation*, InnerClasses, Signature

# kotlinx.serialization: @Serializable クラスのシリアライザを残す
-keepclassmembers class com.ttech.tripshiori.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.tripshiori.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ttech.tripshiori.**$$serializer { *; }
