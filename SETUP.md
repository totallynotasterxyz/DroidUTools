# DroidUtools — Architecture & Setup Guide

**DroidUtools** is a full-featured iOS device manager (a modern 3uTools alternative) designed to run directly on a **rooted Android host phone** (Samsung Galaxy A54, Android 14+, One UI 6, Linux kernel 5.15+ with systemless root via Magisk, KernelSU, or APatch).

The Android phone acts as the **USB Host** via an OTG adapter or USB-C to Lightning/USB-C cable, interacting directly with iOS devices.

---

## 1. Prerequisites & Root Configuration

### Root Requirements
DroidUtools requires **systemless root** because:
1. **Raw USB Node Permissions**: Android restricts access to `/dev/bus/usb/*/*`. Root is required to execute `chmod -R 0666 /dev/bus/usb` and allow bidirectional bulk and control transfers with Apple devices.
2. **usbmuxd Root Daemon**: Spawns and manages the `usbmuxd` multiplexer daemon as `UID 0`, exposing local sockets for Lockdown (port 62078), AFC, and DebugServer.
3. **Network Tunnels for iOS 17+**: Creates virtual network interfaces (TUN/TAP) and routes QUIC packets for modern CoreDevice / RemoteXPC developer services.

### Root Manager Setup
- **Magisk (v26+)**: Open Magisk app -> Superuser tab -> Grant Superuser access to `DroidUtools`. Ensure Zygisk/MagiskHide is not masking the app.
- **KernelSU**: Open KernelSU Manager -> Superuser -> Enable Superuser toggle for `DroidUtools`.
- **APatch**: Open APatch app -> Superuser tab -> Authorize `DroidUtools`.

---

## 2. Native C/C++ Libraries (CMake & NDK)

DroidUtools interfaces with the Apple mobile device stack via JNI bindings:
- **`libimobiledevice`**: Core lockdown, AFC, diagnostics, and backup protocols.
- **`libusbmuxd`**: Client library for socket multiplexing with usbmuxd.
- **`libplist`**: Apple binary and XML property list serialization.
- **`libimobiledevice-glue`**: Common sockets and threading utilities.
- **`zsign`**: Zero-dependency iOS app code signer (replaces macOS `codesign`).

### Cross-compiling for Android `arm64-v8a`:
1. Clone the cross-compilation toolchain or use Android NDK `r26b+`:
   ```bash
   export ANDROID_NDK_HOME=/path/to/ndk
   export TOOLCHAIN=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64
   export TARGET=aarch64-linux-android
   export API=24
   export CC=$TOOLCHAIN/bin/${TARGET}${API}-clang
   export CXX=$TOOLCHAIN/bin/${TARGET}${API}-clang++
   ```
2. Build OpenSSL 3.x for arm64-v8a:
   ```bash
   ./Configure android-arm64 -D__ANDROID_API__=24 no-shared
   make -j$(nproc) && make install
   ```
3. Build libplist, libimobiledevice-glue, and libimobiledevice:
   ```bash
   ./autogen.sh --host=$TARGET --prefix=/opt/arm64-sysroot --enable-static --disable-shared
   make -j$(nproc) && make install
   ```
4. Place compiled `.so` files into:
   `app/src/main/jniLibs/arm64-v8a/libdroidutools_native.so`

---

## 3. Python via Chaquopy (`pymobiledevice3`)

For modern iOS 17+ CoreDevice, RemotePairing, and DVT screenshot streaming, DroidUtools integrates `pymobiledevice3` via Chaquopy:

### Chaquopy Gradle Configuration
```kotlin
// app/build.gradle.kts
plugins {
    id("com.chaquo.python")
}

chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            install("pymobiledevice3>=4.0.0")
            install("cryptography")
            install("construct")
            install("aiohttp")
        }
    }
}
```

---

## 4. USB Connection Flow

1. Plug an OTG adapter into the Samsung Galaxy A54.
2. Connect your iPhone or iPad using a Lightning or USB-C cable.
3. When prompted on Android, allow USB host access for DroidUtools.
4. Unlock the iPhone and tap **"Trust This Computer"** when prompted.
5. The `PairingManager` will automatically execute `RPPairing` (for iOS 17+) or `Lockdownd` (classic fallback) and arm the active badge on the dashboard.
