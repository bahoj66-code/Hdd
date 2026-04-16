# Hay Day Backup

An Android app that adds a floating backup button to copy your Hay Day save data using Shizuku's elevated shell access.

## What it does

- Copies `/data/data/com.supercell.hayday/shared_prefs/` → `/sdcard/HayDayBackups/<timestamp>/`
- A draggable floating button sits over any screen — tap it to trigger a backup instantly
- Uses [Shizuku](https://shizuku.rikka.app/) for privileged file access (no root required)

## Requirements

| Requirement | Notes |
|---|---|
| Android 8.0+ | API 26+ |
| [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) | Must be running before launching this app |
| Overlay permission | Needed for the floating button |

## How to build

1. Open this folder in **Android Studio Hedgehog** or later
2. Let Gradle sync finish
3. Build → **Build APK** (or run on a connected device with `./gradlew installDebug`)

## How to use

1. Start Shizuku on your device (via ADB or wireless debugging)
2. Launch **Hay Day Backup**
3. Grant Shizuku permission when prompted
4. Grant the Overlay (draw over other apps) permission
5. Tap **Show Floating Button**
6. Switch to Hay Day (or any other app)
7. Tap the green floating button whenever you want to save your progress
8. A toast confirms success. Backups appear in `/sdcard/HayDayBackups/`

## Restore

To restore, copy the `.xml` files from a backup folder back into
`/data/data/com.supercell.hayday/shared_prefs/` using Shizuku / ADB:

```sh
adb shell
cp -r /sdcard/HayDayBackups/2024-01-01_12-00-00/. \
      /data/data/com.supercell.hayday/shared_prefs/
```

## Project structure

```
app/src/main/
├── kotlin/com/haydaybackup/
│   ├── MainActivity.kt          – Permission setup & service toggle
│   ├── FloatingButtonService.kt – Foreground service + draggable FAB
│   └── BackupManager.kt         – Shizuku shell commands for file copy
├── res/
│   ├── layout/activity_main.xml
│   └── values/strings.xml / themes.xml
└── AndroidManifest.xml
```
