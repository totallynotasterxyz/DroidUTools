# Third-Party Terms & Data Handling Summaries

**DroidUtools** interacts with several external services, remote repositories, and open-source packages. Below is a breakdown of what each tool does with your data:

---

### 1. Apple Inc. (Apple ID & Developer Portal)
- **Role:** Authentication & Code Signing
- **Data Handling:** Your Apple ID credentials and two-factor codes are transmitted directly from your device to Apple servers (`gsa.apple.com`, `idmsa.apple.com`) over TLS. The Developer never receives, sees, or intercepts your credentials.
- **Governed by:** Apple Privacy Policy and Apple Developer Agreement.

---

### 2. Anisette Authentication Servers
- **Role:** Device Attestation Headers for Apple ID Login
- **Data Handling:** Generates cryptographic machine identifier headers (`X-Apple-I-MD-M`, `X-Apple-I-MD`). If an external anisette server is configured, authentication payloads pass through that server. **Only use anisette servers that you operate or trust.**
- **Governed by:** Individual server operator terms.

---

### 3. SideStore
- **Role:** On-Device Loopback Sideloading
- **Data Handling:** Installs an iOS client that talks to an on-device VPN loopback server. Your pairing record is injected into SideStore locally. SideStore does not send your identity or apps to third-party cloud servers.
- **License:** GPL-3.0-or-later

---

### 4. LiveContainer & StikDebug
- **Role:** JIT Execution & Shared App Container
- **Data Handling:** Operates entirely within the iOS sandbox to execute guest apps without consuming extra free App ID provisioning slots. No telemetry or tracking is performed.
- **License:** GPL-3.0-or-later

---

### 5. GitHub Releases & Update Checks
- **Role:** Application Updates, Binary Downloads & Source Code Distribution
- **Data Handling:** If update checks are enabled, the app queries the GitHub Releases API to detect new releases and download signed APKs. GitHub receives your Android device's IP address and standard HTTP request headers. No personal data, device identifiers, or root status are transmitted.
- **Governed by:** GitHub Privacy Statement and GitHub Terms of Service.

---

### 6. Local Open-Source Native & Python Libraries
- **Role:** USB Host Multiplexing, Protocol Decoding, Code Signing (libimobiledevice, usbmuxd, pymobiledevice3, zsign, libsu).
- **Data Handling:** These libraries run 100% locally on your phone as native compiled code or Python scripts inside Chaquopy. They make zero outbound network requests on their own.
- **Licenses:** LGPL-2.1-or-later, GPL-3.0-or-later, MIT, Apache 2.0.

---

### Questions or Inquiries
Contact the Developer:  
**`spaminhaler1276@gmail.com`**
