# kotlinx.serialization: @Serializable クラスのシリアライザを残す
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.kawamonn.store.data.api.** {
    *** Companion;
}
-keepclasseswithmembers class com.kawamonn.store.data.api.** {
    kotlinx.serialization.KSerializer serializer(...);
}
