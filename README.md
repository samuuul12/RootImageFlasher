# RootImageFlasher

**RootImageFlasher** is an Android application designed to flash disk images (`.img`) directly to device partitions using root access.

It provides a graphical interface for selecting images, identifying available partitions, and performing flashing operations.

## Features

- Root access detection.
- Automatic partition discovery.
- Image selection through Android's file picker.
- Direct image flashing using `dd`.
- Partition size validation before flashing.
- Protected partition handling.
- Advanced mode for accessing protected partitions.
- Flash operation status and error reporting.
- Success confirmation after flashing.
- Optional device reboot after flashing boot-related images.
- Dark user interface.

## Requirements

- Android 11 or later.
- ARM64 device (`arm64-v8a`).
- Root access through Magisk, KernelSU, or a compatible root solution.
- An image file in RAW `.img` format.
- Android SDK 35 for building the application.
- JDK 17.

## Supported Partitions

The application recognizes the following partition types:

| Category | Partitions |
|---|---|
| Boot | `boot`, `init_boot`, `vendor_boot` |
| Recovery | `recovery` |
| Device configuration | `dtbo` |
| Verified Boot | `vbmeta`, `vbmeta_system`, `vbmeta_vendor` |
| Dynamic partitions | `system`, `vendor`, `product`, `system_ext`, `odm` |
| Additional partitions | `super`, `vendor_dlkm`, `odm_dlkm` |

Availability depends on the device partition layout.

Protected partitions require the application's advanced mode.

## Building

Clone the repository:

```bash
git clone https://github.com/samuuul12/RootImageFlasher.git
cd RootImageFlasher
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

Build the release APK:

```bash
./gradlew assembleRelease
```

The generated APK files are located in:

```text
app/build/outputs/apk/debug/
app/build/outputs/apk/release/
```

## Installation

Install the APK using ADB:

```bash
adb install -r app-release.apk
```

Alternatively, transfer the APK to your Android device and install it manually.

## Usage

1. Open RootImageFlasher.
2. Grant root access when requested.
3. Select the image you want to flash.
4. Choose the destination partition.
5. Review the confirmation dialog.
6. Start the flashing operation.
7. Wait for the completion message.

For boot-related partitions, the application can offer to reboot the device after a successful operation.

## Important Notes

- Root privileges are required.
- Use images specifically intended for your device and partition layout.
- This version expects RAW `.img` images. Android Sparse images must be converted to RAW before flashing.
- Flashing an incorrect image can cause boot failures or damage the device's software installation.
- Do not interrupt an active flashing operation.

## Technology Stack

- Kotlin
- Android SDK
- Jetpack Compose
- Material 3
- Kotlin Coroutines
- Android Root Shell

## License

This project is licensed under the Apache License 2.0.

See the [LICENSE](LICENSE) file for details.

