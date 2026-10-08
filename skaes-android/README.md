# SKAES Distribusiku — APK Android

Android WebView yang membuka https://apps.skaes.co.id/?distribusiku=1&p=dashboard.

## Cara menghasilkan APK tanpa Android Studio
1. Buat repository GitHub baru (lebih aman **private**).
2. Unggah **isi** folder proyek ini ke root repository (termasuk folder `.github`).
3. Buka tab **Actions** > **Build Android APK** > **Run workflow**.
4. Setelah workflow berhasil, unduh artifact **SKAES-Distribusiku-APK**.
5. Ekstrak ZIP artifact; install `app-debug.apk` di HP Android. Izinkan instalasi dari sumber tersebut saat diminta Android.

APK debug untuk pengujian/internal saja. Untuk distribusi resmi/Play Store, buat **signed release APK/AAB** dengan kunci signing yang Anda simpan aman. Jangan mempublikasikan APK debug sebagai versi produksi.

## Catatan
- Login dan data tetap berada di server SKAES, tidak disalin ke APK.
- Perlu internet dan izin akun SKAES.
- Fitur khusus seperti kamera/scanner, cetak Bluetooth, lokasi, notifikasi push, popup pembayaran dan ekspor file perlu pengujian tersendiri karena belum semua didukung pada wrapper dasar.
- Pastikan memiliki izin untuk mengemas situs SKAES sebagai aplikasi Android.
- Mengunduh file melalui DownloadManager menyimpan ke direktori unduhan khusus aplikasi, bukan folder Downloads publik.
