# Octop Mobile v1.2 (WebView)

Build: Android Studio (JDK 17), Gradle sync, Run. minSdk 26.
Server: octop run --host 0.0.0.0 --port 8088

v1.2: splash screen, tema terang/gelap mengikuti sistem (dashboard ikut
prefers-color-scheme), banyak server tersimpan, ukuran teks, pesan error
spesifik (DNS, timeout, HTTP 5xx, sertifikat), keluar akun + hapus data web.
v1.1: notifikasi balasan AI, koneksi latar belakang, kunci aplikasi, offline banner.

Notifikasi memakai heuristik atas frame WebSocket /chat/ws (format belum
terverifikasi). Lihat Pengaturan > Diagnostik notifikasi, sesuaikan
TEXT_KEYS / IGNORED_TYPES di ReplyTracker.kt bila perlu.
Sertifikat HTTPS self-signed ditolak.
