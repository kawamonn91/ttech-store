-keepattributes *Annotation*, InnerClasses
# PdfBox-Android: 暗号化PDF用の BouncyCastle は依存から外しているので、参照だけ残ることを許す
-dontwarn org.bouncycastle.**
# JPEG2000 画像のデコーダ(任意依存)も同梱しない
-dontwarn com.gemalto.jp2.**
# リリース版は実機で確認していないため、PdfBox 本体は難読化・削除しない(フォントなどのリソース読み込みを壊さないため)
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
