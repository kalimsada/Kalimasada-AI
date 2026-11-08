# KALIMASADA 1.0

Aplikasi Android tim AI dengan tiga penyedia: **ChatGPT (OpenAI), Claude (Anthropic), dan Gemini (Google)**.

## Fitur
- Mengirim pertanyaan ke setiap penyedia yang API key-nya sudah dikonfigurasi.
- Menampilkan jawaban masing-masing model dan status keberhasilan.
- Menyimpan API key secara lokal di perangkat Android.
- Build APK debug melalui GitHub Actions.

## Persyaratan penggunaan
Aplikasi ini memerlukan API key resmi dari penyedia yang ingin digunakan. Biaya penggunaan API mengikuti ketentuan masing-masing penyedia. API key tidak disertakan dalam APK dan jangan pernah diunggah ke repository.

## Build
Jalankan workflow **Android APK Build** di tab Actions. APK debug dihasilkan pada `app/build/outputs/apk/debug/app-debug.apk`.
