-keepattributes *Annotation*, InnerClasses

# PDFBoxのJPEG2000対応(JPXFilter)だけが参照する任意のライブラリ。結合・ページ抽出では使わない。
-dontwarn com.gemalto.jp2.**

# PDFBoxは内部でリソース読み込みやリフレクションを使うため、縮小・難読化の対象から外す。
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
