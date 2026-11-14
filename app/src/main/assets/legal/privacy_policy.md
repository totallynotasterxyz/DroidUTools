# Privacy Policy

**Last Updated:** November 2026  
**Document Version:** 1.1.0

This Privacy Policy explains how **DroidUtools** ("the App") handles information. The short answer: **We do not collect, transmit, store, sell, or monitor your personal data. Everything happens locally on your Android device.**

The App is developed by an individual independent software developer located in the United States, referred to as **"the Developer"** (handle: **Aster**). Contact: `spaminhaler1276@gmail.com`.

---

### 1. Zero Data Collection by the Developer
The Developer:
- Operates no analytics servers, telemetry trackers, advertising SDKs, crash report collectors, or remote user databases.
- Has no capability or intention to see your device identifiers (UDID, IMEI, serial), Apple ID, passwords, pairing certificates, installed apps, logs, or network traffic.
- Never sells, leases, or trades any user data to any third party under any circumstance.

---

### 2. On-Device Data Storage
All data necessary for the App to function remains exclusively on your Android host device within its private application sandbox:
- **Local Database (Room):** Device history, UDIDs, device names, battery snapshots, and signing identities are kept in an encrypted/private SQLite database.
- **Credentials & Keys:** Apple ID session tokens, certificates (`.p12`), and private keys are stored on-device using Android Keystore and private encrypted storage.
- **Pairing Records:** Lockdown and RemotePairing records (`.plist`, `.mobiledevicepairing`) are stored locally in the App's private files directory.
- **Preferences:** User settings, dark mode preferences, and consent timestamps are kept in Android DataStore.

You can wipe all locally stored data at any time by going to **Settings > Legal > Withdraw Consent and Wipe Data**.

---

### 3. Direct Third-Party Interactions
When you explicitly initiate operations or enable optional network features, the App communicates directly from your phone to external services:
1. **Apple Inc. Services:** When signing IPAs or verifying certificates using your Apple ID, requests are sent directly to Apple's authentication servers (`gsa.apple.com`, `idmsa.apple.com`). This communication is governed exclusively by Apple's Privacy Policy.
2. **Anisette Authentication Servers:** Generating device attestation data for Apple ID login requires an anisette server. If you configure a custom or public anisette endpoint, requests containing header metadata pass through that endpoint. **The Developer does not own or control third-party anisette servers.**
3. **GitHub Releases (In-App Updates):** If update checks are enabled, the app contacts the GitHub Releases API to look for new releases. GitHub receives your IP address and standard HTTP request headers. GitHub's terms and privacy policy apply. No device identifiers, root state, or personal information are sent.
4. **IPA Download Hosts:** If you download an IPA or tool from a web URL, your IP address and connection details are exposed to that third-party hosting provider.

---

### 4. Children’s Privacy (COPPA)
The App is not directed to individuals under eighteen (18) years of age. The Developer does not knowingly solicit or collect information from children. If you are under 18, you are not authorized to use the App.

---

### 5. California Privacy Rights (CCPA / CPRA)
Under the California Consumer Privacy Act (CCPA) and California Privacy Rights Act (CPRA):
- The Developer does not sell or share personal information.
- The Developer does not collect categories of personal information for commercial purposes.
- Because all information is stored exclusively on your personal device, you have immediate and complete control to inspect, export, or delete your data directly within the App.

---

### 6. Updates to this Policy
If this Privacy Policy is updated, the document version will change, and the App will present a summary of changes on the next launch before resuming operations.

---

### 7. Inquiries
If you have questions about this Privacy Policy, reach out to the Developer at:  
**`spaminhaler1276@gmail.com`**
