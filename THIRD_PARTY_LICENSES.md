# Third-Party Licenses & GPL-3.0 Compatibility Audit

This document lists third-party software, libraries, and components utilized by **DroidUtools**, their applicable licenses, copyright notices, and a GPL-3.0-or-later compatibility analysis.

---

## GPL-3.0-or-Later Compatibility Matrix

DroidUtools is licensed under the **GNU General Public License v3.0 or later (GPL-3.0-or-later)**. All linked libraries and incorporated components comply with the licensing terms of GPL-3.0:

| Component | License | GPL-3.0 Compatibility | Notes / Rationale |
| :--- | :--- | :--- | :--- |
| **AndroidX & Jetpack Libraries** | Apache License 2.0 | Compatible | Permissible under GPLv3 section 7 (Apache 2.0 patent license & terms). |
| **Jetpack Compose & Material 3** | Apache License 2.0 | Compatible | Standard Android UI framework. |
| **Kotlin Standard Library & Coroutines** | Apache License 2.0 | Compatible | Core runtime libraries. |
| **libsu** | Apache License 2.0 | Compatible | Root execution shell and IPC library by John Wu (topjohnwu). |
| **OkHttp & Retrofit** | Apache License 2.0 | Compatible | HTTP client and API integration by Square, Inc. |
| **Moshi** | Apache License 2.0 | Compatible | JSON serialization and parsing library by Square, Inc. |
| **Coil** | Apache License 2.0 | Compatible | Async image loader for Jetpack Compose. |
| **libimobiledevice** | LGPL-2.1-or-later | Compatible | LGPL libraries can be dynamically or statically linked into GPLv3 programs. |
| **libusbmuxd** | LGPL-2.1-or-later | Compatible | USB multiplexor client library. |
| **usbmuxd daemon** | GPL-2.0-or-later / GPL-3.0-or-later | Compatible | Native daemon for USB host communication. |
| **libplist** | LGPL-2.1-or-later | Compatible | Apple Binary and XML Property List library. |
| **zsign** | MIT License | Compatible | Open-source IPA code signature utility by zhlynn. |
| **pymobiledevice3** | GPL-3.0-or-later | Compatible | Python implementation of iOS services by Doron Zavelevsky. |
| **Chaquopy** | MIT License / Proprietary Runtime Exception | Compatible | Embedded Python engine for Android. |

---

## Detailed Third-Party Licenses

### 1. Android Jetpack, AndroidX, and Kotlin Components
- **Copyright:** (C) 2018-2026 The Android Open Source Project, JetBrains s.r.o.
- **License:** Apache License, Version 2.0
- **Summary:** Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at `http://www.apache.org/licenses/LICENSE-2.0`.

---

### 2. libsu
- **Copyright:** (C) 2017-2026 John Wu (topjohnwu)
- **License:** Apache License, Version 2.0
- **Summary:** Used for root shell execution and IPC via Magisk/KernelSU.

---

### 3. OkHttp & Retrofit
- **Copyright:** (C) 2012-2026 Square, Inc.
- **License:** Apache License, Version 2.0
- **Summary:** HTTP client and REST adapter used for downloading IPAs and Anisette interaction.

---

### 4. Moshi
- **Copyright:** (C) 2015-2026 Square, Inc.
- **License:** Apache License, Version 2.0
- **Summary:** Used for parsing JSON data, pairing configs, and catalog formats.

---

### 5. libimobiledevice, libusbmuxd, and libplist
- **Copyright:** (C) 2009-2026 Nikias Bassen, Martin Szulecki, Christophe Fergeau, and contributors.
- **License:** GNU Lesser General Public License, Version 2.1 or later (LGPL-2.1-or-later)
- **Summary:** Provides native iOS protocol implementations (lockdownd, afc, installation_proxy, mobile_image_mounter, syslog).

---

### 6. zsign
- **Copyright:** (C) 2019-2026 zhlynn
- **License:** MIT License
- **Text:**
```
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

### 7. pymobiledevice3
- **Copyright:** (C) 2020-2026 Doron Zavelevsky and contributors
- **License:** GNU General Public License, Version 3.0 or later (GPL-3.0-or-later)
- **Summary:** RemoteXPC, RemotePairing, CoreDevice, and Developer Disk mounting infrastructure.

---

## Source Code Inquiries

In accordance with GPL-3.0 requirements, the complete corresponding machine-readable source code for DroidUtools and any covered modifications may be requested by contacting:

- **Developer:** Aster
- **Email:** `spaminhaler1276@gmail.com`
