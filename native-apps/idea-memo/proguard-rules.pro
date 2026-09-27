-keepattributes *Annotation*, InnerClasses, Signature

# kotlinx.serialization: @Serializable クラスのシリアライザを残す
-keepclassmembers class com.ttech.ideamemo.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.ideamemo.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ttech.ideamemo.**$$serializer { *; }
