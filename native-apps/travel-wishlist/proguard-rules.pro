-keepattributes *Annotation*, InnerClasses, Signature

# kotlinx.serialization: @Serializable クラスのシリアライザを残す
-keepclassmembers class com.ttech.travelwishlist.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.travelwishlist.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ttech.travelwishlist.**$$serializer { *; }
