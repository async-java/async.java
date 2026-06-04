#!/usr/bin/env bash
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: $0 <GPG_KEY_ID>" >&2
  exit 64
fi

key_id="$1"

gpg --keyserver hkps://keys.openpgp.org --send-keys "$key_id"
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys "$key_id"
