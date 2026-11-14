#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Generates or exports the DroidUtools Android release upload keystore for local builds & GitHub Actions.
set -euo pipefail

KEYSTORE_FILE="${1:-my-upload-key.jks}"
KEY_ALIAS="${2:-upload}"
PROPS_FILE="keystore.properties"

if ! command -v keytool >/dev/null 2>&1; then
  echo "Error: 'keytool' (JDK 17+) not found in PATH." >&2
  exit 1
fi

if [ ! -f "$KEYSTORE_FILE" ]; then
  echo "Generating PKCS12 release keystore at $KEYSTORE_FILE (alias: $KEY_ALIAS)..."
  if command -v openssl >/dev/null 2>&1; then
    PASSWORD=$(openssl rand -hex 16)
  elif command -v python3 >/dev/null 2>&1; then
    PASSWORD=$(python3 -c 'import secrets, string; print("".join(secrets.choice(string.ascii_letters + string.digits) for _ in range(28)))')
  else
    PASSWORD=$(dd if=/dev/urandom bs=32 count=1 2>/dev/null | base64 | tr -dc 'A-Za-z0-9' | cut -c1-28)
  fi
  keytool -genkeypair -v \
    -keystore "$KEYSTORE_FILE" \
    -storetype PKCS12 \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -alias "$KEY_ALIAS" \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -dname "CN=DroidUtools, OU=Mobile, O=DroidUtools, L=Unknown, ST=Unknown, C=US"

  cat > "$PROPS_FILE" <<EOF
storeFile=../$KEYSTORE_FILE
storePassword=$PASSWORD
keyAlias=$KEY_ALIAS
keyPassword=$PASSWORD
EOF
  echo "Created $PROPS_FILE (gitignored)."
else
  echo "Using existing keystore: $KEYSTORE_FILE"
  if [ -f "$PROPS_FILE" ]; then
    PASSWORD=$(grep '^storePassword=' "$PROPS_FILE" | cut -d= -f2-)
  else
    read -r -s -p "Enter keystore password for $KEYSTORE_FILE: " PASSWORD
    echo ""
  fi
fi

echo ""
echo "=== Signing Certificate Fingerprint ==="
keytool -list -v -keystore "$KEYSTORE_FILE" -storepass "$PASSWORD" | grep -E "Alias name:|SHA256:" || true

echo ""
echo "=== GitHub Actions Secrets Checklist ==="
echo "Add these under GitHub -> Settings -> Secrets and variables -> Actions -> Repository secrets:"
echo "  KEYSTORE_BASE64   : $(base64 < "$KEYSTORE_FILE" | tr -d '\n')"
echo "  KEYSTORE_PASSWORD : <value from $PROPS_FILE -> storePassword>"
echo "  KEY_ALIAS         : $KEY_ALIAS"
echo "  KEY_PASSWORD      : <value from $PROPS_FILE -> keyPassword>"
