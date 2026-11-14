# Disclaimers & Technical Notices

**Last Updated:** November 2026  
**Document Version:** 1.0.0

Please read these technical notices and disclaimers before utilizing advanced iOS management features in **DroidUtools**.

---

### 1. Root & Host Device Security Notice
- DroidUtools requires **root access (via Magisk or KernelSU)** on the host Android phone to communicate directly with USB hardware `/dev/bus/usb` and launch the `usbmuxd` root daemon.
- Operating rooted software inherently carries security and stability implications. Granting root permission to any application provides broad system privileges.
- You are solely responsible for securing your Android host device, maintaining trusted root modules, and monitoring root authorizations.

---

### 2. iOS Hardware, Firmware & Data Risk
- Interfacing with low-level iOS protocols (including Lockdown, RemotePairing, AFC, MobileImageMounter, and Developer Disk Images) carries potential risks of communication interruption, temporary lockouts, or data corruption if cables are unplugged during critical transfers.
- **Always maintain a full, independent backup of your iOS device (via iCloud or official computer backup) before modifying applications, system logs, or developer settings.**
- The Developer is not responsible for device soft-bricks, recovery mode loops, boot issues, or hardware damage.

---

### 3. Apple ID & Provisioning Policy
- Sideloading applications and generating development certificates relies on Apple Developer Program guidelines.
- Standard free Apple accounts are limited to:
  - 3 active sideloaded application slots per device.
  - 7-day certificate expiration window.
  - Maximum of 10 App IDs registered per 7 days.
- Repeatedly creating and revoking development certificates, using untrusted anisette servers, or automating signing actions may prompt Apple security flags, password resets, or account restrictions.
- You are solely responsible for compliance with Apple's Developer Agreement and Terms of Service.

---

### 4. Trademark & Non-Affiliation
- **Apple, iPhone, iPad, iOS, iPadOS, Lightning, macOS, and App Store** are registered trademarks of Apple Inc.
- **3uTools** is a trademark of its respective owners.
- **SideStore, LiveContainer, StikDebug** are independent community open-source projects.
- DroidUtools is an independent open-source utility developed by an individual developer (**Aster**) and has **no affiliation, sponsorship, or endorsement** from Apple Inc., 3uTools, or any referenced third party.

---

### 5. Questions & Contact
For technical questions or license compliance requests, contact:  
**`spaminhaler1276@gmail.com`**
