# 🗺️ Wear Maps: Standalone Wear OS 2D Map Navigator

[![Wear OS](https://img.shields.io/badge/Platform-Wear%20OS%204-blue.svg)](https://developer.android.com/wear)
[![Release](https://img.shields.io/badge/Release-v1.0.0-green.svg)](https://github.com/ajimsjames/WearMaps/releases/tag/v1.0.0)
[![License](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

A standalone **offline 2D vector map tile navigator**, **hardware compass heading rotator**, and **waypoint manager** designed specifically for Wear OS smartwatches (Samsung Galaxy Watch 4/5/6, Pixel Watch, TicWatch). Built with Kotlin and Jetpack Compose for Wear OS by **Aju George**.

---

## ✨ Features

* **🗺️ 2D OpenStreetMap Tile Engine**: High-performance 2D map tile renderer with custom OLED Dark Mode filtering to prevent screen burn-in and minimize battery draw on AMOLED smartwatch displays.
* **🧭 Compass Heading Rotation**: Hardware rotation vector sensor continuously rotates the map based on your wrist direction ("Heading-Up" 🗺️ vs "North-Up" 🧭 mode).
* **🔄 Rotary Bezel & Touch Zoom**: Smoothly zoom in/out using physical watch rotating bezels (Galaxy Watch 6) or touch `+` / `-` controls.
* **🎯 GPS Real-Time Location Lock**: Pulsing live location dot with instant map re-centering.
* **📍 Offline Waypoint Saver**: Pin and save custom offline locations (e.g. Parked Car 🚗, Hotel 🏨, Home 🏠).
* **⬇️ Offline Region Pre-Downloader**: Download and store map tiles locally on watch storage for full offline navigation without smartphone or cellular data.

---

## 📸 Navigation Controls

1. **Bezel / Crown Scroll**: Smooth Zoom In / Zoom Out.
2. **Touch Drag**: Drag across screen to pan map.
3. **🎯 Button**: Recenter map to your current GPS position.
4. **🧭 / 🗺️ Button**: Toggle between North-Up and Heading-Up compass map rotation.
5. **📍 Pin Button**: Drop and save offline location markers.
6. **⬇️ Offline Button**: Pre-download current map region for offline use.

---

## 🚀 Installation

### Option 1: Direct ADB Wireless Install
Download the pre-compiled [`app-release.apk`](https://github.com/ajimsjames/WearMaps/releases/download/v1.0.0/app-release.apk) from the [v1.0.0 Release Page](https://github.com/ajimsjames/WearMaps/releases/tag/v1.0.0) and install over ADB:

```bash
adb connect <your-watch-ip>:5555
adb install -r app-release.apk
```

### Option 2: Build from Source
```bash
git clone https://github.com/ajimsjames/WearMaps.git
cd WearMaps
./gradlew assembleRelease
```
The APK will be generated at `app/build/outputs/apk/release/app-release.apk`.

---

## 🛠️ Tech Stack & Requirements

* **Platform**: Wear OS 3.0+ (API 30+)
* **Language**: Kotlin 1.9
* **UI Framework**: Jetpack Compose for Wear OS & Skia Canvas
* **Map Engine**: OpenStreetMap (OSM) Slippy Tiles with Disk Caching
* **Hardware Sensors**: `Sensor.TYPE_ROTATION_VECTOR`, `LocationManager` (GPS)

---

## 👨‍💻 Developer & Author

* **Author**: Aju George ([@ajimsjames](https://github.com/ajimsjames))
* **Email**: `ajimsjames@gmail.com`

---

## 📜 License

This project is licensed under the MIT License - see the LICENSE file for details.
