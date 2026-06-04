#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
  echo "usage: $0 <GPG_KEY_ID> [/tmp/maven-gpg-private-key.asc]" >&2
  exit 64
fi

key_id="$1"
output="${2:-/tmp/maven-gpg-private-key.asc}"

if ! gpg --list-secret-keys "$key_id" >/dev/null 2>&1; then
  echo "missing local GPG secret key: $key_id" >&2
  exit 66
fi

umask 077
gpg --armor --export-secret-keys "$key_id" >"$output"

echo "exported private key to $output"
echo "next: export MAVEN_GPG_PRIVATE_KEY=\"\$(cat $output)\""
