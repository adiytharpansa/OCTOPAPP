# Octop Mobile — aturan R8 rilis
# Seluruh kode aplikasi sendiri dipertahankan (jembatan WebView OctopBridge
# dipanggil dari JavaScript lewat refleksi nama)
-keep class cloud.octop.mobile.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
