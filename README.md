# Tugas Besar 1 IF2211 Strategi Algoritma

> Penerapan Algoritma Greedy pada Bot Battlecode 2025

## Daftar Isi

- [Deskripsi Singkat](#deskripsi-singkat)
- [Algoritma Greedy per Bot](#algoritma-greedy-per-bot)
  - [main_bot](#1-main_bot)
  - [alternative_bots_1](#2-alternative_bots_1)
  - [alternative_bots_2](#3-alternative_bots_2)
- [Requirement](#requirement)
- [Instalasi dan Build](#instalasi-dan-build)
- [Menjalankan Pertandingan](#menjalankan-pertandingan)
- [Struktur Proyek](#struktur-proyek)
- [Author](#author)

## Deskripsi Singkat

Proyek ini mengimplementasikan kecerdasan buatan robot untuk kompetisi Battlecode 2025 menggunakan pendekatan **Algoritma Greedy**. Setiap robot membuat keputusan optimal secara lokal pada setiap giliran (turn) untuk mencapai hasil global terbaik. Terdapat 3 package bot yang dibangun dengan strategi greedy yang berbeda-beda.

## Requirement

| Komponen  | Versi Minimum |
|-----------|--------------|
| Java JDK  | 21           |
| Gradle    | 8.x (sudah termasuk via Gradle Wrapper) |
| Git       | 2.x          |

- Sistem Operasi: Windows / macOS / Linux
- Battlecode 2025 Engine & Client (sudah termasuk di repository sebagai artifact)

## Instalasi dan Build

### 1. Clone Repository

```bash
git clone https://github.com/Fariz36/STIMA-battle.git
cd STIMA-battle
```

### 2. Pastikan Java 21 Terinstall

Cek versi Java:
```bash
java -version
```

Jika belum terinstall, download JDK 21 dari [Adoptium](https://adoptium.net/) atau [Oracle](https://www.oracle.com/java/technologies/downloads/).

### 3. Build Project

**Windows:**
```bash
./gradlew build
```

**Linux/macOS:**
```bash
chmod +x gradlew
./gradlew build
```

Build akan mengompilasi semua package bot dan memverifikasi engine artifact.

### 4. Verifikasi Bot

```bash
./gradlew listPlayers
```

Akan menampilkan daftar bot yang tersedia: `main_bot`, `alternative_bots_1`, `alternative_bots_2`, `examplefuncsplayer`.

## Menjalankan Pertandingan

### Menggunakan Gradle

Edit file `gradle.properties` untuk mengatur tim dan peta:

```properties
teamA=main_bot
teamB=alternative_bots_2
maps=DefaultSmall
```

Lalu jalankan:

```bash
./gradlew run
```

### Atau langsung via command line:

```bash
./gradlew run -PteamA=main_bot -PteamB=alternative_bots_2 -Pmaps=DefaultSmall
```

### Melihat Daftar Peta

```bash
./gradlew listMaps
```

### Hasil Pertandingan

File replay tersimpan di folder `matches/` dengan format `.bc25`. Buka menggunakan Battlecode Client yang ada di folder `client/`.

## Struktur Proyek

```
Tubes1/
├── README.md                        # Dokumentasi proyek
│
├── src/                             # Source code semua bot
│   ├── main_bot/                    # Bot utama 
│   │   ├── RobotPlayer.java        
│   │   ├── BaseRobot.java          
│   │   ├── Unit.java               
│   │   ├── Tower.java              
│   │   └── Units/
│   │       ├── Soldier.java        
│   │       ├── Mopper.java         
│   │       └── Splasher.java       
│   │
│   ├── alternative_bots_1/          # Bot alternatif 1 
│   │   ├── RobotPlayer.java        
│   │   ├── RobotMovement.java      
│   │   └── LocationMemory.java     
│   │
│   ├── alternative_bots_2/          # Bot alternatif 2 
│   │   ├── RobotPlayer.java        
│   │   ├── Soldier.java            
│   │   ├── Mopper.java             
│   │   ├── Splasher.java           
│   │   └── Tower.java              
│   │
│   └── examplefuncsplayer/          # Bot contoh bawaan Battlecode
│       └── RobotPlayer.java
├── doc/                             # Laporan Tugas Besar
```

## Author

| Nama | NIM |
|------|-----|
| Benedict Darrel Setiawan | 13524057 |
| Raymond Jonathan Dwi Putra J | 13524059 |
| Reynard Anderson Wijaya | 13524111 |
