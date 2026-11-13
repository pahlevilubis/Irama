# Irama — Pemutar Musik Offline untuk Android

Dibuat oleh **Pahlevi Lubis**.

Aplikasi pemutar lagu yang sudah ada di HP (offline), dibuat untuk Samsung Galaxy S21 Ultra
(jalan juga di HP Android 10 ke atas lainnya).

## Fitur

- **Perpustakaan lagu otomatis** — semua lagu di HP terbaca sendiri, dibagi tab **Lagu, Album, Artis, Folder**, plus **pencarian**
- **Putar di background** — tetap jalan saat layar mati / buka aplikasi lain, ada kontrol di **notifikasi & layar kunci**, tombol **headset Bluetooth** berfungsi
- **Equalizer** dengan kurva yang bisa digeser langsung per frekuensi
- **13 Mode Suara**: Normal, Bass Mantap, Pop, Rock, Dangdut, Jazz, Klasik, Vokal, Hip-Hop, Elektronik, Akustik, Treble, Santai (+ Kustom)
- **Efek**: Bass Boost, Surround 3D, Penguat Volume — semua tersimpan otomatis
- **Acak (shuffle)**, **Ulangi** (semua / satu lagu), **Timer Tidur** (15–90 menit)
- **Download audio dari YouTube** (untuk pemakaian pribadi) — tempel link, atau di aplikasi YouTube
  ketuk *Bagikan → Irama*. Hasilnya file M4A lengkap dengan judul, artis, dan cover di folder
  `Music/Irama`, langsung muncul di daftar lagu. Memakai library [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor).
  Kalau suatu saat gagal terus, biasanya YouTube berubah: naikkan versi NewPipeExtractor di
  `app/build.gradle.kts` lalu build ulang.
- Auto pause saat earphone dicabut, tampilan gelap dengan cover album

---

## Cara 1 — Build lewat GitHub (paling gampang, tidak perlu install apa-apa)

1. Buat akun gratis di **github.com** (kalau belum punya).
2. Klik **New repository** → beri nama `Irama` → pilih **Private** → **Create repository**.
3. Di halaman repo, klik **uploading an existing file**.
4. Ekstrak `Irama.zip` di laptop, buka folder `Irama`, lalu **pilih semua isinya** (termasuk folder `.github`)
   dan drag ke halaman upload GitHub → klik **Commit changes**.
5. Buka tab **Actions** → tunggu proses **Build APK Irama** sampai centang hijau (±5–8 menit).
6. Klik hasil build tersebut → di bagian **Artifacts** klik **Irama-APK** untuk download.
7. Ekstrak zip yang terdownload → dapat file **`app-release.apk`**.

> Kalau folder `.github` tidak ikut ter-upload, build tidak akan jalan. Cek di repo harus ada
> `.github/workflows/build-apk.yml`.

## Cara 2 — Build pakai Android Studio di laptop

1. Install **Android Studio** (developer.android.com/studio).
2. **File → Open** → pilih folder `Irama` → tunggu proses *Gradle Sync* selesai (pertama kali agak lama,
   butuh internet). Kalau muncul tawaran *upgrade AGP*, abaikan dulu.
3. Buka menu **View → Tool Windows → Build Variants**, ubah `app` menjadi **release**
   (versi release lebih ringan & lancar).
4. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. File APK ada di `app/build/outputs/apk/release/app-release.apk`.

## Install di HP Samsung

1. Kirim `app-release.apk` ke HP (kabel USB, Google Drive, atau WhatsApp ke diri sendiri).
2. Buka file APK lewat aplikasi **My Files / File Saya**.
3. Kalau diminta, izinkan **Install unknown apps** untuk My Files.
4. Kalau muncul peringatan Play Protect, pilih **Install anyway / Tetap instal**.
5. Buka **Irama** → ketuk **Izinkan Akses Musik**.

### Supaya musik tidak berhenti sendiri di background (khusus Samsung)

**Settings → Apps → Irama → Battery → Unrestricted (Tidak dibatasi).**

## Catatan

- Lagu di bawah 30 detik (voice note, nada dering) sengaja tidak ditampilkan.
- Lagu baru belum muncul? Ketuk ikon **↻ (Pindai ulang)** di kanan atas.
- Surround 3D paling terasa pakai earphone/headset.
- Kalau **Dolby Atmos** Samsung aktif, efeknya ikut bertumpuk. Kalau suara terlalu berat, matikan salah satu.
- Update versi baru bisa langsung ditimpa (tanda tangan aplikasi tetap, file `app/irama.keystore`).

## Struktur kode (Kotlin + Jetpack Compose + Media3)

```
app/src/main/java/com/irama/player/
├── MainActivity.kt          Layar utama
├── PlaybackService.kt       Service pemutar (background, notifikasi)
├── audio/AudioFx.kt         Equalizer, preset, bass, surround, loudness
├── audio/SleepTimer.kt      Timer tidur
├── data/MusicRepository.kt  Baca lagu dari penyimpanan HP
├── data/Song.kt             Model lagu, album, artis, folder
└── ui/                      Tampilan: Home, Now Playing, Equalizer
```

Preset equalizer bisa diubah di `audio/AudioFx.kt` (nilai dB untuk 60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz).
