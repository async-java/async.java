#!/usr/bin/env bash
set -euo pipefail

repo="${1:-async-java/async.java}"

required=(
  CENTRAL_USERNAME
  CENTRAL_PASSWORD
  MAVEN_GPG_PRIVATE_KEY
  MAVEN_GPG_PASSPHRASE
)

missing=0
for name in "${required[@]}"; do
  if [ -z "${!name:-}" ]; then
    echo "missing env var: $name" >&2
    missing=1
  fi
done

if [ "$missing" -ne 0 ]; then
  cat >&2 <<'EOF'

Expected environment:

  CENTRAL_USERNAME=... \
  CENTRAL_PASSWORD=... \
  MAVEN_GPG_PRIVATE_KEY="$(cat /tmp/maven-gpg-private-key.asc)" \
  MAVEN_GPG_PASSPHRASE=... \
  scripts/set-release-secrets.sh

EOF
  exit 64
fi

gh auth status >/dev/null

for name in "${required[@]}"; do
  printf '%s' "${!name}" | gh secret set "$name" --repo "$repo"
done

echo "release secrets configured for $repo"
