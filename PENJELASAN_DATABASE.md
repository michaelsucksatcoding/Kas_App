# Pemenuhan Kompetensi: Database Lokal Android

Proyek **Class Fund** memenuhi lima kompetensi berikut melalui aplikasi Java Android yang menyimpan data secara lokal menggunakan SQLite. Implementasi utama berada pada `app/src/main/java/com/example/kas_app/DatabaseHelper.java`; pengujian otomatisnya berada di `app/src/test/java/com/example/kas_app/`.

## 1. Konsep penyimpanan lokal dengan SQLite

SQLite adalah basis data relasional yang berjalan langsung di perangkat. Data disimpan dalam file database aplikasi, sehingga operasi baca/tulis tidak memerlukan server atau koneksi internet. Pada proyek ini, `DatabaseHelper` mengelola file `kas_app.db` dengan `SQLiteOpenHelper`. Akses tulis menggunakan `getWritableDatabase()`, sedangkan baca menggunakan `getReadableDatabase()`. `SQLiteOpenHelper` membuat database saat pertama kali dibutuhkan dan memanggil migrasi ketika versi skema berubah.

## 2. Struktur tabel melalui SQLiteOpenHelper

`DatabaseHelper` membuat tiga tabel pada `onCreate()`:

| Tabel | Isi utama |
|---|---|
| `table_students` | ID siswa dan nama |
| `table_payments` | ID siswa, bulan, tahun, dan nominal pembayaran |
| `table_ledger` | Tanggal, deskripsi, jenis transaksi, nominal, dan tautan pembayaran opsional |

Kolom ID menggunakan `INTEGER PRIMARY KEY AUTOINCREMENT`. Database memiliki versi skema dan migrasi bertahap yang menambah indeks serta kolom tanpa menghapus data lama.

## 3. Operasi Create, Read, Update, Delete

Metode pada `DatabaseHelper` membungkus operasi SQLite untuk siswa, pembayaran, dan catatan kas. Create menggunakan `insert()`, Read menggunakan query/cursor, Update menggunakan `update()`, dan Delete menggunakan `delete()`. Operasi pembayaran Fund Data dan pencatatan kas terkait memakai transaksi agar perubahan yang berpasangan tersimpan secara konsisten. Penghapusan siswa ditolak jika masih memiliki riwayat pembayaran.

## 4. Menampilkan data dengan RecyclerView

Data hasil query dipetakan ke adapter dan ditampilkan melalui `RecyclerView`: daftar siswa pada `FundDataActivity`/`StudentAdapter`, riwayat pembayaran pada `StudentPaymentBottomSheet`/`PaymentAdapter`, dan buku kas pada `FundReportActivity`/`LedgerAdapter`. Setelah perubahan data, tampilan dimuat ulang dari database.

## 5. Pengujian dan debugging

Proyek memiliki pengujian unit berbasis JUnit dan Robolectric yang menggunakan SQLite sungguhan, termasuk pengujian skema/migrasi (`DatabaseHelperTest`), CRUD siswa (`StudentCrudTest`), CRUD pembayaran (`PaymentCrudTest`), aturan saldo (`LedgerBalanceRuleTest`), dan konsistensi pembayaran dengan buku kas (`FundDataLedgerTest`). Pengujian dijalankan dengan:

```powershell
.\gradlew.bat testDebugUnitTest
```

Status verifikasi: seluruh 147 tes unit lulus dengan menjalankan `.\gradlew.bat testDebugUnitTest`. Saat pemeriksaan awal, satu tes UI profil gagal karena event sentuhan dikirim menggunakan koordinat header yang tidak sesuai dengan target view pada Robolectric. Tes diperbaiki agar mengirim tap pada koordinat lokal tombol profil dan memverifikasi listener kliknya. Tes inti SQLite/CRUD serta tes UI kini seluruhnya lulus.

## Kesimpulan

Proyek memenuhi kompetensi konsep SQLite lokal, pembuatan skema melalui `SQLiteOpenHelper`, operasi CRUD, penayangan data pada `RecyclerView`, serta pengujian dan debugging. Seluruh 147 tes unit berhasil dijalankan; dengan dokumentasi kompetensi ini, proyek tuntas untuk ruang lingkup yang diminta.
