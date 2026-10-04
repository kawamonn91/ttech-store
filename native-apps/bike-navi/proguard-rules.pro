-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# kotlinx.serialization: @Serializable クラスのシリアライザを残す
-keepclassmembers class com.ttech.bikenavi.** {
    *** Companion;
}
-keepclasseswithmembers class com.ttech.bikenavi.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ttech.bikenavi.**$$serializer { *; }
