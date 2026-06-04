#!/usr/bin/env bash
set -euo pipefail

repo="${1:-async-java/async.java}"
mode="post-tag"
if [ "${1:-}" = "--pre-tag" ]; then
  mode="pre-tag"
  repo="${2:-async-java/async.java}"
fi

pom_value() {
  local expression="$1"
  local fallback_tag="$2"
  if command -v mvn >/dev/null 2>&1; then
    mvn -B -q -DforceStdout help:evaluate -Dexpression="$expression"
  else
    awk -F'[<>]' -v tag="$fallback_tag" '$2 == tag { print $3; exit }' pom.xml
  fi
}

if command -v mvn >/dev/null 2>&1; then
  version="$(mvn -B -q -DforceStdout help:evaluate -Dexpression=project.version)"
else
  version="$(awk -F'[<>]' '/<version>/ { print $3; exit }' pom.xml)"
fi
group_id="$(pom_value project.groupId groupId)"
artifact_id="$(pom_value project.artifactId artifactId)"
group_path="$(printf '%s' "$group_id" | tr . /)"
metadata_path="$group_path/$artifact_id/maven-metadata.xml"
tag="v$version"
branch="$(git branch --show-current)"
required=(
  CENTRAL_USERNAME
  CENTRAL_PASSWORD
  MAVEN_GPG_PRIVATE_KEY
  MAVEN_GPG_PASSPHRASE
)

fail=0
ci_gpg_secret_ready=0

say() {
  printf '%s\n' "$*"
}

check() {
  local label="$1"
  shift
  if "$@"; then
    say "ok: $label"
  else
    say "missing: $label"
    fail=1
  fi
}

if gh auth status >/dev/null 2>&1; then
  say "ok: gh authentication"
else
  say "missing: gh authentication"
  fail=1
fi

if secret_output="$(gh secret list --repo "$repo" 2>&1)"; then
  say "ok: GitHub repo secret API access ($repo)"
  secret_names="$(printf '%s\n' "$secret_output" | awk '{print $1}')"
  for name in "${required[@]}"; do
    if printf '%s\n' "$secret_names" | grep -qx "$name"; then
      say "ok: repo secret $name"
    else
      say "missing: repo secret $name"
      fail=1
    fi
  done
  if printf '%s\n' "$secret_names" | grep -qx MAVEN_GPG_PRIVATE_KEY \
      && printf '%s\n' "$secret_names" | grep -qx MAVEN_GPG_PASSPHRASE; then
    ci_gpg_secret_ready=1
  fi
else
  say "missing: GitHub repo secret API access ($repo)"
  printf '%s\n' "$secret_output" | sed 's/^/  gh: /'
  for name in "${required[@]}"; do
    say "missing: repo secret $name (unable to verify)"
  done
  fail=1
fi

if sh -c 'command -v gpg >/dev/null 2>&1 && gpg --list-secret-keys >/dev/null 2>&1 && [ -n "$(gpg --list-secret-keys --with-colons 2>/dev/null | awk -F: '\''$1 == "sec" { print; exit }'\'')" ]'; then
  say "ok: local GPG secret key"
elif [ "$ci_gpg_secret_ready" -eq 1 ]; then
  say "ok: CI GPG signing secrets"
  say "pending: local GPG secret key (needed only for local deploys)"
else
  say "missing: local GPG secret key"
  fail=1
fi
check "current branch is pushed ($branch)" git ls-remote --exit-code origin "refs/heads/$branch"

if git ls-remote --exit-code origin "refs/tags/$tag" >/dev/null 2>&1; then
  say "present: remote tag $tag"
elif [ "$mode" = "pre-tag" ]; then
  say "pending: remote tag $tag"
else
  say "missing: remote tag $tag"
  fail=1
fi

metadata_url="https://repo.maven.apache.org/maven2/$metadata_path"
if curl -fsSL "$metadata_url" >/tmp/async-java-release-metadata.xml 2>/dev/null; then
  if grep -q "<version>$version</version>" /tmp/async-java-release-metadata.xml; then
    say "present: Maven Central version $version"
  else
    say "missing: Maven Central version $version"
    fail=1
  fi
elif [ "$mode" = "pre-tag" ]; then
  say "pending: Maven Central metadata at $metadata_url"
else
  say "missing: Maven Central metadata at $metadata_url"
  fail=1
fi

exit "$fail"
