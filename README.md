# 🗺️ WearMaps (v1.2.0)

**Standalone Offline Map Navigation & GNSS Satellite Tracking App for Wear OS (Samsung Galaxy Watch 6)**

Developed by **Aju George**.

---

## ✨ Features

- 🎯 **Continuous GPS Auto-Center Tracking (`🎯 Mode`)**: Automatically keeps the map centered on your position as you walk, drive, or run. Manually dragging the map smoothly pauses auto-centering for free exploration.
- 📡 **GNSS Satellite Counter Overlay**: Real-time display of connected GPS satellites (`e.g. 📡 8/14 Sats`) with color-coded signal quality status (🟢 6+ Sats, 🟡 3-5 Sats, 🔴 <3 Sats).
- ⬇️ **Offline Region Pre-Downloader**: Caches map tiles locally on watch storage for complete offline usage without internet or phone pairing.
- 🧭 **Heading Up / North Up Toggle**: Rotates the map dynamically using watch hardware compass sensors.
- 📌 **Custom Waypoint Pins**: Save favorite coordinates directly on the map with custom labels and coordinate management.
- ⚙️ **Bezel-Aligned Navigation & About Dialog**: Curved bezel top navigation bar (`CurvedLayout`) tailored for 480×480 px circular smartwatch displays.

---

## 🛠️ Architecture & Tech Stack

- **Framework**: Android Wear OS (Min SDK 30 / Target SDK 33)
- **UI Engine**: Wear Compose + Jetpack Compose + CurvedLayout
- **Map Engine**: OpenStreetMap raster tiles fetched & rendered via native Skia 2D Canvas with dark mode color matrix filter.
- **Hardware Integration**: Android LocationManager + GnssStatus.Callback + Compass Sensors.

---

## 📦 Installation

```bash
# Connect to Galaxy Watch 6 via Wireless ADB
adb connect <WATCH_IP>:<PORT>

# Build and Install Release APK
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 📄 License & Credits

Created and maintained by **Aju George**. Distributed for Wear OS devices.
