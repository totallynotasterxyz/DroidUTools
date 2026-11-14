# DroidUtools — Technical Capabilities & Limitations Matrix

This document provides a realistic, honest technical breakdown of iOS management capabilities, dependencies, and constraints when operating from a rooted Android USB host.

---

## 1. Operating System & Pairing Constraints

| Feature | iOS 12 - 16.7 | iOS 17.0 - 17.3.1 | iOS 17.4+ | Notes |
| :--- | :---: | :---: | :---: | :--- |
| **Lockdownd Pairing** | Full | Fallback | Fallback | Classic SSL certificate handshake. Compatible with SideStore and AltStore. |
| **RPPairing (RemotePairing)** | Not Supported | Supported | Mandatory | Modern CoreDevice tunnel over QUIC. Required for Xcode-less debugserver attach on iOS 17.4+. |
| **Direct USB Host Control** | Yes | Yes | Yes | Requires Android root (`chmod 0666 /dev/bus/usb`). |
| **Wi-Fi Lockdown Sync** | Yes | Yes | Yes | Must be paired over USB first, and "Sync over Wi-Fi" enabled in Lockdown plist. |

---

## 2. Developer Mode & JIT Debugging

- **Developer Mode (iOS 16+)**:
  - Must be manually enabled on the iOS device: *Settings → Privacy & Security → Developer Mode → Toggle ON → Restart iPhone → Tap Turn On*.
  - Developer Mode is **mandatory** for installing development-signed IPAs, attaching debugserver, and enabling JIT on emulators (Dolphin, PojavLauncher, Flycast).
- **Personalized DDI (Developer Disk Image)**:
  - iOS 17+ dropped static Developer Disk Images in favor of dynamic personalized DDI downloaded via Apple TSS servers. DroidUtools downloads the corresponding manifest and mounts it over usbmuxd.
- **JIT Enabler**:
  - Requires debugserver attachment (`process launch --stop-at-entry`).
  - Without a jailbreak, JIT processes terminate if the host drops connection; DroidUtools keeps a foreground service alive to maintain the debugserver session.

---

## 3. Apple ID & Sideloading Signing Limits

- **Free Apple ID Accounts (Non-Developer)**:
  - 7-day certificate validity (apps expire after 7 days and must be re-signed).
  - Maximum of **3 active sideloaded apps** per device.
  - Maximum 10 App IDs per 7 days.
  - SideStore uses 1 App ID and 1 active app slot, allowing 2 additional sideloaded apps.
- **Paid Apple Developer Program ($99/year)**:
  - 365-day certificate validity.
  - Up to 100 devices per device type.
  - No 3-app sideload limit; supports all entitlements (VPN, Push Notifications, Extended Virtual Addressing).
- **Enterprise Certificates**:
  - Unlimited devices, but revoked frequently by Apple.

---

## 4. Jailbreak vs Non-Jailbreak Matrix

| Action | Non-Jailbroken (Stock) | Jailbroken (rootful / rootless) |
| :--- | :---: | :---: |
| **IPA Sideloading** | Yes (via free/paid Apple ID or developer profile) | Yes (AppSync Unified bypasses all signing) |
| **JIT Memory Permissions** | Yes (via debugserver attach) | Yes (direct `cs_ops` memory patch) |
| **App Sandbox Access** | Restricted to `house_arrest` containers for debuggable apps | Full root filesystem access (`/var/mobile`, `/Library`) |
| **Screen Mirroring** | Low-latency DVT screenshot stream (~30-60 FPS) | Direct frame buffer streaming via VNC/OpenSSH |
| **Remote Control (Taps/Swipes)**| WebDriverAgent runner app required | Direct simulated touch events (`cycript` / `hid`) |
| **Location Simulation** | Yes (via DVT Location Simulation service) | Yes (direct GPS hook) |
| **Reboot / Recovery / DFU** | Yes (standard recovery and reboot commands) | Yes |

---

## 5. Summary of Samsung Galaxy A54 Root Environment

- **USB Host**: USB 2.0 Type-C interface. Provides 5V/1.5A power delivery to the connected iPhone.
- **Root Managers**: Verified on Magisk 26.x, KernelSU 0.9.x, and APatch.
- **SELinux**: Fully compatible with SELinux Enforcing via domain policy rules for `usbmuxd`.
