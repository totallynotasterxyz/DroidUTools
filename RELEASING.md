# Releasing DroidUtools

This document explains how to sign, build, tag, and distribute releases for **DroidUtools** using GitHub Actions.

> **Important:** Every public release APK **must** be signed with the exact same keystore. Android verifies APK signing certificates before allowing an in-place update; if the certificate changes, users and the in-app updater cannot install the new version over an existing installation.

---

## 1. Generate or Export the Release Keystore

Run the included helper script (`bash scripts/setup-keystore.sh`) or use `keytool` (included with JDK 17+) to generate a PKCS12 keystore matching `app/build.gradle.kts` (`my-upload-key.jks`, alias `upload`):

```bash
keytool -genkeypair \
  -v \
  -keystore my-upload-key.jks \
  -storetype PKCS12 \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -alias upload
```

Store `my-upload-key.jks` and its passwords in a password manager / encrypted backup. **Never commit `.jks`, `.keystore`, `keystore.properties`, or `signing.properties` to Git.**

---

## 2. Base64-Encode the Keystore

Convert the `.jks` file into a single-line Base64 string:

- **Linux:**
  ```bash
  base64 -w 0 my-upload-key.jks > keystore.b64
  ```
- **macOS:**
  ```bash
  base64 -i my-upload-key.jks | tr -d '\n' > keystore.b64
  ```

Copy the contents of `keystore.b64` and delete the temporary `.b64` file afterward.

---

## 3. Configure GitHub Repository Secrets

In your GitHub repository, navigate to **Settings → Secrets and variables → Actions → Repository secrets** and add all four secrets:

| Secret Name | Value |
| --- | --- |
| `KEYSTORE_BASE64` | Single-line Base64 string of `my-upload-key.jks` |
| `KEYSTORE_PASSWORD` | Password for the keystore |
| `KEY_ALIAS` | Key alias (`upload`) |
| `KEY_PASSWORD` | Password for the key alias (same as keystore password for PKCS12) |

If any of these secrets are missing during a workflow run, `.github/workflows/build.yaml` decodes `debug.keystore.base64` and builds a debug-signed APK so consecutive CI builds share the same signing certificate. Release builds should always have all four secrets configured.

---

## 4. Publishing a Tagged Release (Recommended)

1. Update any release-facing changelogs or legal doc versions if needed and commit to `main`.
2. Create an annotated semantic version tag matching `v*.*.*` (for example `v1.2.0` or `v1.2.0-rc.1`):
   ```bash
   git tag -a v1.2.0 -m "Release v1.2.0"
   git push origin v1.2.0
   ```
3. GitHub Actions automatically:
   - Builds the native toolchain (`arm64-v8a`), runs `lintDebug` and `testDebugUnitTest`, and assembles a signed, R8-minified APK.
   - Derives `versionName` from the tag (stripping leading `v`) and `versionCode` from the monotonic Git commit count.
   - Generates `DroidUtools-v{versionName}-{versionCode}-release.apk` and its `.sha256` checksum file.
   - Generates build provenance attestation (`actions/attest-build-provenance`).
   - Publishes the GitHub Release with categorized commit release notes (`Added`, `Fixed`, `Changed`).

---

## 5. Manual Release or Debug Build (`workflow_dispatch`)

From the **Actions** tab in GitHub, select **Build & Release** → **Run workflow**:

- **`build_type`**: `release` (default) or `debug`
- **`create_release`**: Set `true` to publish a GitHub Release even without pushing a tag
- **`prerelease`**: Set `true` to mark the GitHub Release as a pre-release
- **`release_notes`**: Optional custom Markdown notes prepended to the release body

---

## 6. Enabling Nightly Builds

Nightly builds run via the daily cron schedule in `.github/workflows/build.yaml` and only execute if there are new commits since the last `nightly` tag.

To enable nightlies:
1. Go to **Settings → Secrets and variables → Actions → Variables**.
2. Add a repository variable named `ENABLE_NIGHTLY` with value `true`.
3. To pause nightlies, set `ENABLE_NIGHTLY` to `false` or delete the variable.

---

## 7. Local Signed Release Builds

For local signed testing without CI, copy `keystore.properties.example` to `keystore.properties` in the repository root (already gitignored):

```properties
storeFile=../my-upload-key.jks
storePassword=YOUR_KEYSTORE_PASSWORD
keyAlias=upload
keyPassword=YOUR_KEY_PASSWORD
```

Then run:

```bash
gradle assembleRelease -PversionCode=$(git rev-list --count HEAD) -PversionName=1.2.0
```

---

## 8. Lost Signing Key Recovery

If `droidutools-release.jks` and its passwords are permanently lost:

1. **In-place updates are impossible:** Android's PackageManager and DroidUtools' `UpdateVerifier` strictly reject any APK whose signing certificate does not match the currently installed app.
2. **Recovery steps:**
   - Generate a brand-new keystore (Step 1) and update the four GitHub repository secrets (Step 3).
   - Clearly inform users in the release notes that the signing key rotated.
   - Users must **export/back up any pairing files or app settings**, manually **uninstall** the existing DroidUtools app, and **clean-install** the newly signed APK.
   - Because `UpdateVerifier` validates the installed certificate against the downloaded APK certificate before launching the installer, the in-app updater will block installation across mismatched keys to protect against tampering.
