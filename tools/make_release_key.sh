#!/usr/bin/env bash
# Creates a private signing key for SakuriKey releases and prints what to add as GitHub secrets.
#
#   tools/make_release_key.sh [output-folder]
#
# The key is written outside the repository (default: ~/sakurikey-release-key) and must never be committed.
# Needs `keytool` (it comes with any JDK) and `openssl` or `head`/`base64` for random passwords.
set -euo pipefail

out="${1:-$HOME/sakurikey-release-key}"
mkdir -p "$out"
file="$out/sakurikey-release.jks"
alias_name="sakurikey"

if [ -e "$file" ]; then
  echo "A key already exists at $file. Not overwriting it." >&2
  exit 1
fi

pass="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)"

keytool -genkeypair -v -keystore "$file" -alias "$alias_name" -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$pass" -keypass "$pass" -dname "CN=SakuriKey, O=Personal, C=US" >/dev/null

echo
echo "Key created: $file"
echo "Back this file up somewhere safe. If you lose it, updates can't be signed the same way again."
echo
echo "Add these four secrets in the repository (Settings > Secrets and variables > Actions):"
echo
echo "  RELEASE_KEYSTORE_PASSWORD = $pass"
echo "  RELEASE_KEY_ALIAS         = $alias_name"
echo "  RELEASE_KEY_PASSWORD      = $pass"
echo "  RELEASE_KEYSTORE_BASE64   = (the long text printed next)"
echo
base64 -w0 "$file" 2>/dev/null || base64 -i "$file"
echo
